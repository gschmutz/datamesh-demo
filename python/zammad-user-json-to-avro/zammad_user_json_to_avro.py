#!/usr/bin/env python3
"""Consume raw Zammad user JSON from Kafka and re-emit it as Avro.

Reads JSON string records from ``priv.zammad.user.v1``, reshapes each message to
the ``AgentUser`` Avro schema (dropping unknown fields and coercing types), and
produces Confluent Schema-Registry-serialized Avro to
``pub.zammad.agent-user.state.v1``.

Examples
--------
Stream from Kafka to Kafka (needs a running broker + Schema Registry)::

    python zammad_user_json_to_avro.py

Test the transformation without Kafka::

    python zammad_user_json_to_avro.py --from-file sample.json --to-file out.avro
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import sys
from pathlib import Path

from schema_conform import ConformError, conform

SCHEMA_DIR = Path(__file__).resolve().parents[2] / "java/datamesh-meta/target/generated-sources/avro/schema"
DEFAULT_SCHEMA = SCHEMA_DIR / "ticket/AgentUser.avsc"


def resolve_schema_path(path: Path) -> Path | None:
    """Return ``path`` if it exists, else search the generated schema dir for AgentUser.avsc."""
    if path.exists():
        return path
    return next(SCHEMA_DIR.rglob("AgentUser.avsc"), None)


def load_schema(path: Path) -> dict:
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


class FilteredOut(Exception):
    """Raised when a message does not carry the required role id."""


def transform(raw_value: str | bytes | dict, schema: dict, required_role_id: int | None = None) -> dict:
    """Parse a raw Zammad user JSON message and conform it to the AgentUser schema.

    Filtering on ``role_ids`` happens on the raw payload, before conforming, since
    the AgentUser schema itself no longer carries ``role_ids``.
    """
    if isinstance(raw_value, (str, bytes)):
        payload = json.loads(raw_value)
    else:
        payload = dict(raw_value)
    if not isinstance(payload, dict):
        raise ConformError(f"expected JSON object, got {type(payload).__name__}")
    if required_role_id is not None:
        role_ids = payload.get("role_ids") or []
        if required_role_id not in [int(r) for r in role_ids]:
            raise FilteredOut(f"role_ids {role_ids} does not contain {required_role_id}")
    return conform(payload, schema)


def run_file_mode(args: argparse.Namespace, schema: dict) -> int:
    text = Path(args.from_file).read_text(encoding="utf-8")
    stripped = text.lstrip()
    records = (
        [json.loads(line) for line in text.splitlines() if line.strip()]
        if stripped.startswith("{") and "\n{" in text and not stripped.startswith("{\n")
        else [json.loads(text)]
    )
    conformed = []
    for rec in records:
        try:
            conformed.append(transform(rec, schema, args.role_id))
        except FilteredOut as exc:
            print(f"Filtered out: {exc}", file=sys.stderr)

    if args.to_file:
        from fastavro import parse_schema, writer

        parsed = parse_schema(schema)
        with open(args.to_file, "wb") as out:
            writer(out, parsed, conformed)
        print(f"Wrote {len(conformed)} Avro record(s) to {args.to_file}")
    else:
        for rec in conformed:
            print(json.dumps(rec, indent=2, ensure_ascii=False))
    return 0


def run_kafka_mode(args: argparse.Namespace, schema: dict) -> int:
    from confluent_kafka import Consumer, KafkaError, Producer
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroSerializer
    from confluent_kafka.serialization import MessageField, SerializationContext

    consumer = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": args.group_id,
            "auto.offset.reset": args.offset_reset,
            "enable.auto.commit": False,
        }
    )
    consumer.subscribe([args.input_topic])

    sr_client = SchemaRegistryClient({"url": args.schema_registry_url})
    avro_serializer = AvroSerializer(
        sr_client,
        json.dumps(schema),
        conf={"auto.register.schemas": args.auto_register},
    )
    producer = Producer({"bootstrap.servers": args.bootstrap_servers})

    running = {"flag": True}

    def _stop(_signum, _frame):
        running["flag"] = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    def _delivery(err, msg):
        if err is not None:
            print(f"Delivery failed: {err}", file=sys.stderr)

    processed = 0
    failed = 0
    filtered = 0
    print(
        f"Consuming JSON from '{args.input_topic}', producing Avro to "
        f"'{args.output_topic}' (Ctrl-C to stop)..."
    )
    try:
        while running["flag"]:
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                print(f"Consumer error: {msg.error()}", file=sys.stderr)
                continue
            raw = msg.value()
            if raw is None:
                consumer.commit(msg)
                continue
            try:
                record = transform(raw, schema, args.role_id)
            except FilteredOut:
                filtered += 1
                consumer.commit(msg)
                continue
            except (json.JSONDecodeError, ConformError) as exc:
                failed += 1
                print(
                    f"Skipping bad message at {msg.topic()}[{msg.partition()}]"
                    f"@{msg.offset()}: {exc}",
                    file=sys.stderr,
                )
                consumer.commit(msg)
                continue

            ctx = SerializationContext(args.output_topic, MessageField.VALUE)
            # Keep the input key (the Zammad user id); fall back to the record id.
            key = msg.key() or str(record["id"]).encode("utf-8")
            producer.produce(
                topic=args.output_topic,
                key=key,
                value=avro_serializer(record, ctx),
                on_delivery=_delivery,
            )
            producer.poll(0)
            consumer.commit(msg)
            processed += 1
            if processed % 100 == 0:
                print(f"  ...{processed} messages converted")
    finally:
        producer.flush(10)
        consumer.close()
        print(f"Done. Converted={processed}, filtered={filtered}, skipped={failed}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--schema",
        type=Path,
        default=Path(os.environ.get("AVRO_SCHEMA", DEFAULT_SCHEMA)),
        help="Path to the AgentUser .avsc (default: generated schema).",
    )
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--input-topic", default=os.environ.get("INPUT_TOPIC", "priv.zammad.user.v1"))
    p.add_argument("--output-topic", default=os.environ.get("OUTPUT_TOPIC", "pub.zammad.agent-user.state.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "zammad-user-json-to-avro"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument("--auto-register", action="store_true", default=os.environ.get("AUTO_REGISTER", "true").lower() == "true")
    p.add_argument(
        "--role-id",
        type=int,
        default=int(os.environ.get("ROLE_ID", "2")),
        help="Only convert users whose role_ids contain this id (default: 2).",
    )
    p.add_argument("--from-file", help="Read raw JSON from this file instead of Kafka (single object or NDJSON).")
    p.add_argument("--to-file", help="With --from-file: write conformed records to this Avro container file.")
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    schema_path = resolve_schema_path(args.schema)
    if schema_path is None:
        print(f"Schema not found: {args.schema}\nRun 'mvn generate-sources' in java/datamesh-meta first.", file=sys.stderr)
        return 2
    schema = load_schema(schema_path)
    if args.from_file:
        return run_file_mode(args, schema)
    return run_kafka_mode(args, schema)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
