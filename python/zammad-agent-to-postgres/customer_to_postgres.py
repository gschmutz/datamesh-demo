#!/usr/bin/env python3
"""Consume Avro Customer records from Kafka and load them into PostgreSQL.

Reads ``pub.zammad.customer.v1`` (Confluent Schema-Registry Avro, conforming to
customer/Customer.avdl) and upserts each record into the normalized schema from
scripts/postgresql/cr_customer.sql (organization + customer +
customer_secondary_organization).

Kafka offsets are committed only after the DB transaction commits
(at-least-once).
"""

from __future__ import annotations

import argparse
import os
import signal
import sys

from mapping import persist_customer


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--input-topic", default=os.environ.get("INPUT_TOPIC", "pub.zammad.customer.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "zammad-customer-to-postgres"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument(
        "--dsn",
        default=os.environ.get("PG_DSN", "postgresql://demo:abc123!@dataplatform:5432/demodb"),
        help="PostgreSQL connection string.",
    )
    p.add_argument(
        "--db-schema",
        default=os.environ.get("DB_SCHEMA", "customer"),
        help="PostgreSQL schema holding the customer tables (search_path).",
    )
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    import psycopg
    from psycopg import sql
    from confluent_kafka import Consumer, KafkaError
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroDeserializer
    from confluent_kafka.serialization import MessageField, SerializationContext

    sr_client = SchemaRegistryClient({"url": args.schema_registry_url})
    avro_deserializer = AvroDeserializer(sr_client)  # reader schema = writer schema from registry

    consumer = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": args.group_id,
            "auto.offset.reset": args.offset_reset,
            "enable.auto.commit": False,
        }
    )
    consumer.subscribe([args.input_topic])

    conn = psycopg.connect(args.dsn, autocommit=False)
    with conn.cursor() as cur:
        cur.execute(sql.SQL("SET search_path TO {}, public").format(sql.Identifier(args.db_schema)))
    conn.commit()

    running = {"flag": True}

    def _stop(_s, _f):
        running["flag"] = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    processed = failed = 0
    print(f"Consuming Customer Avro from '{args.input_topic}' into PostgreSQL schema '{args.db_schema}' (Ctrl-C to stop)...")
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
                customer = avro_deserializer(raw, ctx)
                with conn.cursor() as cur:
                    persist_customer(cur, customer)
                conn.commit()
            except Exception as exc:  # noqa: BLE001 - keep the consumer alive
                conn.rollback()
                failed += 1
                print(
                    f"Failed message at {msg.topic()}[{msg.partition()}]@{msg.offset()}: "
                    f"{type(exc).__name__}: {exc}",
                    file=sys.stderr,
                )
                consumer.commit(msg)
                continue

            consumer.commit(msg)
            processed += 1
            if processed % 100 == 0:
                print(f"  ...{processed} customers loaded")
    finally:
        consumer.close()
        conn.close()
        print(f"Done. loaded={processed}, failed={failed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
