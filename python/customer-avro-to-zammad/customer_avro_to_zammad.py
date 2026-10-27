#!/usr/bin/env python3
"""Consume Avro ``CustomerState`` records from Kafka and create Zammad customers.

Reads ``pub.cus.customer.state.v1`` (Confluent Schema-Registry Avro, conforming
to customer/state/CustomerState-v1.avdl) and upserts each customer as a Zammad
user with the ``Customer`` role through the Zammad REST API. Kafka offsets are
committed only after Zammad accepted the request and the Zammad user id was
stored together with the Customer id (UUID) in ``customer_id_mapping`` (see
cr_id_mapping.sql), giving at-least-once delivery.

Example
-------
    python customer_avro_to_zammad.py \
        --bootstrap-servers localhost:9092 \
        --schema-registry-url http://localhost:8081 \
        --zammad-url http://localhost:8080 \
        --zammad-token <api token> \
        --dsn "postgresql://demo:abc123!@localhost:5432/demodb"
"""

from __future__ import annotations

import argparse
import os
import signal
import sys

from mapping import ZammadClient, save_customer_mapping, to_zammad_user


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--input-topic", default=os.environ.get("INPUT_TOPIC", "pub.cus.customer.state.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "customer-avro-to-zammad"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument("--zammad-url", default=os.environ.get("ZAMMAD_URL", "http://dataplatform:28425"))
    p.add_argument("--zammad-token", default=os.environ.get("ZAMMAD_TOKEN"), help="Zammad API token (preferred).")
    p.add_argument("--zammad-user", default=os.environ.get("ZAMMAD_USER"), help="Basic-auth user (if no token).")
    p.add_argument("--zammad-password", default=os.environ.get("ZAMMAD_PASSWORD"), help="Basic-auth password.")
    p.add_argument(
        "--dsn",
        default=os.environ.get("PG_DSN", "postgresql://demo:abc123!@dataplatform:5432/demodb"),
        help="PostgreSQL connection string for the id mapping table.",
    )
    p.add_argument(
        "--pg-schema",
        default=os.environ.get("PG_SCHEMA", "ticket"),
        help="PostgreSQL schema holding customer_id_mapping (set as search_path).",
    )
    p.add_argument("--dry-run", action="store_true", help="Print the Zammad payloads instead of calling the API.")
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    if not args.dry_run and not (args.zammad_token or args.zammad_user):
        print("Set --zammad-token (or --zammad-user/--zammad-password), or use --dry-run.", file=sys.stderr)
        return 2

    import psycopg
    from confluent_kafka import Consumer, KafkaError
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroDeserializer
    from confluent_kafka.serialization import MessageField, SerializationContext

    sr_client = SchemaRegistryClient({"url": args.schema_registry_url})
    avro_deserializer = AvroDeserializer(sr_client)  # uses the writer schema from the registry

    zammad = None if args.dry_run else ZammadClient(
        args.zammad_url, token=args.zammad_token, user=args.zammad_user, password=args.zammad_password
    )

    conn = None if args.dry_run else psycopg.connect(
        args.dsn, autocommit=False, options=f"-c search_path={args.pg_schema}"
    )

    consumer = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": args.group_id,
            "auto.offset.reset": args.offset_reset,
            "enable.auto.commit": False,
        }
    )
    consumer.subscribe([args.input_topic])

    running = {"flag": True}

    def _stop(_signum, _frame):
        running["flag"] = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    processed = 0
    failed = 0
    print(f"Consuming Avro from '{args.input_topic}' into Zammad at {args.zammad_url} (Ctrl-C to stop)...")
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
                state = avro_deserializer(raw, ctx)
                payload = to_zammad_user(state["customer"])
                if zammad is None:
                    print(payload)
                else:
                    action, zammad_id = zammad.upsert_customer(payload)
                    with conn.cursor() as cur:
                        save_customer_mapping(cur, zammad_id, state["customer"]["id"])
                    conn.commit()
                    print(f"  {action} Zammad user {zammad_id} (login={payload['login']})")
            except Exception as exc:  # noqa: BLE001 - keep the consumer alive
                if conn is not None:
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
    finally:
        consumer.close()
        if conn is not None:
            conn.close()
        print(f"Done. Sent={processed}, failed={failed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
