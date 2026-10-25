#!/usr/bin/env python3
"""Consume Avro ``TicketAction (v1)`` records from Kafka and load them into
PostgreSQL.

Reads the ``pub.zammad`` topic (Confluent Schema-Registry Avro), fans each
denormalized ``{ticket, article}`` message out into the normalized relational
tables from ``cr_ticket.sql``, and upserts them idempotently. Kafka offsets are
committed only after the database transaction for that message commits, giving
at-least-once delivery.

Example
-------
    python zammad_avro_to_postgres.py \
        --bootstrap-servers localhost:9092 \
        --schema-registry-url http://localhost:8081 \
        --dsn "postgresql://demo:abc123!@localhost:5432/demodb"
"""

from __future__ import annotations

import argparse
import os
import signal
import sys

from mapping import persist_action


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--input-topic", default=os.environ.get("INPUT_TOPIC", "pub.zammad.ticket.action.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "zammad-avro-to-postgres"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument(
        "--dsn",
        default=os.environ.get("PG_DSN", "postgresql://postgres:abc123!@dataplatform:5432/demodb"),
        help="PostgreSQL connection string (libpq / psycopg DSN).",
    )
    p.add_argument(
        "--pg-schema",
        default=os.environ.get("PG_SCHEMA", "ticket"),
        help="PostgreSQL schema holding the ticket tables (set as search_path).",
    )
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    import psycopg
    from confluent_kafka import Consumer, KafkaError
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroDeserializer
    from confluent_kafka.serialization import MessageField, SerializationContext

    sr_client = SchemaRegistryClient({"url": args.schema_registry_url})
    avro_deserializer = AvroDeserializer(sr_client)  # uses the writer schema from the registry

    consumer = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": args.group_id,
            "auto.offset.reset": args.offset_reset,
            "enable.auto.commit": False,
        }
    )
    consumer.subscribe([args.input_topic])

    conn = psycopg.connect(args.dsn, autocommit=False, options=f"-c search_path={args.pg_schema}")

    running = {"flag": True}

    def _stop(_signum, _frame):
        running["flag"] = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    processed = 0
    failed = 0
    print(f"Consuming Avro from '{args.input_topic}' into PostgreSQL (Ctrl-C to stop)...")
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
                ctx = SerializationContext(msg.topic(), MessageField.VALUE)
                action = avro_deserializer(raw, ctx)
                with conn.cursor() as cur:
                    persist_action(cur, action)
                conn.commit()
            except Exception as exc:  # noqa: BLE001 - keep the consumer alive
                conn.rollback()
                failed += 1
                print(
                    f"Failed message at {msg.topic()}[{msg.partition()}]@{msg.offset()}: "
                    f"{type(exc).__name__}: {exc}",
                    file=sys.stderr,
                )
                # Commit the offset so a poison message doesn't block the topic.
                consumer.commit(msg)
                continue

            consumer.commit(msg)
            processed += 1
            if processed % 100 == 0:
                print(f"  ...{processed} messages loaded")
    finally:
        consumer.close()
        conn.close()
        print(f"Done. Loaded={processed}, failed={failed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
