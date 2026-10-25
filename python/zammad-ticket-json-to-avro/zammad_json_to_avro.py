#!/usr/bin/env python3
"""Consume raw Zammad JSON from Kafka and re-emit it as Avro.

Reads JSON string records from the ``priv.zammad.ticket.v1`` topic, reshapes each
message to the ``TicketAction`` (v1) Avro schema (dropping unknown fields and coercing
types), and produces Confluent Schema-Registry-serialized Avro to an output
topic.

Examples
--------
Stream from Kafka to Kafka (default, needs a running broker + Schema Registry)::

    python zammad_json_to_avro.py

Test the transformation without Kafka: read JSON from a file, write an Avro
container file you can inspect with ``fastavro`` / ``avro-tools``::

    python zammad_json_to_avro.py --from-file sample.json --to-file out.avro
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import sys
from pathlib import Path

from schema_conform import ConformError, conform

DEFAULT_SCHEMA = (
    Path(__file__).resolve().parents[2]
    / "java/datamesh-meta/target/generated-sources/avro"
    / "schema/ticket/action/TicketAction.avsc"
)


def load_schema(path: Path) -> dict:
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


def transform(raw_value: str | bytes | dict, schema: dict) -> dict:
    """Parse a raw JSON message and conform it to the Avro schema.

    Zammad emits ``{ticket, article}`` on every ticket event, but on an update
    that did not add a new article the ``article`` object is empty/sparse. The
    schema makes ``article`` optional, so normalize any article without an ``id``
    to ``null`` instead of failing to conform it.
    """
    if isinstance(raw_value, (str, bytes)):
        payload = json.loads(raw_value)
    else:
        payload = dict(raw_value)
    article = payload.get("article")
    if not isinstance(article, dict) or "id" not in article:
        payload["article"] = None
    return conform(payload, schema)


# --------------------------------------------------------------------------- #
# File mode (no Kafka) -- handy for testing the mapping offline.
# --------------------------------------------------------------------------- #
def run_file_mode(args: argparse.Namespace, schema: dict) -> int:
    text = Path(args.from_file).read_text(encoding="utf-8")
    # Support either a single JSON object or newline-delimited JSON.
    stripped = text.lstrip()
    records = (
        [json.loads(line) for line in text.splitlines() if line.strip()]
        if stripped.startswith("{") and "\n{" in text and not stripped.startswith("{\n")
        else [json.loads(text)]
    )
    conformed = [transform(rec, schema) for rec in records]

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


# --------------------------------------------------------------------------- #
# Kafka mode.
# --------------------------------------------------------------------------- #
def run_kafka_mode(args: argparse.Namespace, schema: dict) -> int:
    from confluent_kafka import Consumer, KafkaError
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroSerializer
    from confluent_kafka.serialization import (
        MessageField,
        SerializationContext,
        StringSerializer,
    )
    from confluent_kafka import Producer

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
    key_serializer = StringSerializer("utf_8")
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
                record = transform(raw, schema)
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
            value_bytes = avro_serializer(record, ctx)
            key = msg.key()
            producer.produce(
                topic=args.output_topic,
                key=key_serializer(key.decode("utf-8")) if key else None,
                value=value_bytes,
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
        print(f"Done. Converted={processed}, skipped={failed}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--schema",
        type=Path,
        default=Path(os.environ.get("AVRO_SCHEMA", DEFAULT_SCHEMA)),
        help="Path to the TicketAction .avsc (default: generated schema).",
    )
    # Kafka options
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--input-topic", default=os.environ.get("INPUT_TOPIC", "priv.zammad.ticket.v1"))
    p.add_argument("--output-topic", default=os.environ.get("OUTPUT_TOPIC", "pub.zammad.ticket.action.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "zammad-json-to-avro"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument("--auto-register", action="store_true", default=os.environ.get("AUTO_REGISTER", "true").lower() == "true")
    # File (offline) options
    p.add_argument("--from-file", help="Read raw JSON from this file instead of Kafka (single object or NDJSON).")
    p.add_argument("--to-file", help="With --from-file: write conformed records to this Avro container file.")
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.schema.exists():
        print(f"Schema not found: {args.schema}\nRun 'mvn generate-sources' in java/datamesh-meta first.", file=sys.stderr)
        return 2
    schema = load_schema(args.schema)
    if args.from_file:
        return run_file_mode(args, schema)
    return run_kafka_mode(args, schema)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
