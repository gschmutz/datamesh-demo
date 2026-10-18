#!/usr/bin/env python3
"""Build the Customer data product from Zammad user + organization streams.

- Consumes raw JSON users from ``priv.zammad.user.v1``.
- Keeps only users holding the customer role (``role_ids`` contains 3).
- Enriches each from an organization lookup cache built from
  ``priv.zammad.organization.v1``:
    * ``organization``             <- user.organization_id   (primary org)
    * ``secondary_organizations``  <- user.organization_ids  (the rest)
- Serializes to Avro (schema: customer/Customer.avsc) via Confluent Schema
  Registry and produces to ``pub.zammad.customer.v1``.

Offsets are committed only after the message is produced (at-least-once).
"""

from __future__ import annotations

import argparse
import os
import signal
import sys
from pathlib import Path

from mapping import conform_customer, conform_organization, is_customer
from org_cache import OrganizationCache

DEFAULT_SCHEMA = (
    Path(__file__).resolve().parents[2]
    / "java/datamesh-meta/target/generated-sources/avro/schema/customer/Customer.avsc"
)


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "dataplatform:9092"))
    p.add_argument("--schema-registry-url", default=os.environ.get("SCHEMA_REGISTRY_URL", "http://dataplatform:8081"))
    p.add_argument("--user-topic", default=os.environ.get("USER_TOPIC", "priv.zammad.user.v1"))
    p.add_argument("--organization-topic", default=os.environ.get("ORGANIZATION_TOPIC", "priv.zammad.organization.v1"))
    p.add_argument("--output-topic", default=os.environ.get("OUTPUT_TOPIC", "pub.zammad.customer.v1"))
    p.add_argument("--group-id", default=os.environ.get("GROUP_ID", "zammad-customer-enrich"))
    p.add_argument("--offset-reset", default=os.environ.get("OFFSET_RESET", "earliest"), choices=["earliest", "latest"])
    p.add_argument("--customer-role-id", type=int, default=int(os.environ.get("CUSTOMER_ROLE_ID", "3")))
    p.add_argument("--schema", type=Path, default=Path(os.environ.get("CUSTOMER_SCHEMA", DEFAULT_SCHEMA)))
    return p.parse_args(argv)


def resolve_orgs(user: dict, cache: OrganizationCache) -> tuple[dict | None, list[dict], int]:
    """Resolve the primary org and the secondary orgs from the cache.

    Returns (organization, secondary_organizations, miss_count). Secondary orgs
    are all organization_ids except the primary; ids not found in the cache are
    skipped (and counted as misses).
    """
    misses = 0
    primary_id = user.get("organization_id")
    organization = cache.get(primary_id)
    if primary_id is not None and organization is None:
        misses += 1

    secondary: list[dict] = []
    for oid in user.get("organization_ids") or []:
        if primary_id is not None and int(oid) == int(primary_id):
            continue
        org = cache.get(oid)
        if org is None:
            misses += 1
        else:
            secondary.append(org)
    return organization, secondary, misses


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.schema.exists():
        print(f"Schema not found: {args.schema}\nRun 'mvn generate-sources' in java/datamesh-meta first.", file=sys.stderr)
        return 2

    from confluent_kafka import Consumer, KafkaError, Producer
    from confluent_kafka.schema_registry import SchemaRegistryClient
    from confluent_kafka.schema_registry.avro import AvroSerializer
    from confluent_kafka.serialization import MessageField, SerializationContext, StringSerializer
    import json

    # --- organization lookup cache (warm up before processing users) ------ #
    cache = OrganizationCache(args.bootstrap_servers, args.organization_topic)
    cache.start()
    print(f"Warming up organization cache from '{args.organization_topic}'...")
    cache.wait_until_caught_up(timeout=30)
    print(f"  organization cache ready: {cache.size()} organization(s)")

    # --- serializer / producer / consumer --------------------------------- #
    sr_client = SchemaRegistryClient({"url": args.schema_registry_url})
    avro_serializer = AvroSerializer(sr_client, args.schema.read_text(), conf={"auto.register.schemas": True})
    key_serializer = StringSerializer("utf_8")
    producer = Producer({"bootstrap.servers": args.bootstrap_servers})

    consumer = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": args.group_id,
            "auto.offset.reset": args.offset_reset,
            "enable.auto.commit": False,
        }
    )
    consumer.subscribe([args.user_topic])

    running = {"flag": True}

    def _stop(_s, _f):
        running["flag"] = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    def _delivery(err, msg):
        if err is not None:
            print(f"Delivery failed: {err}", file=sys.stderr)

    produced = skipped = failed = 0
    print(
        f"Consuming users from '{args.user_topic}', producing customers to "
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
                user = json.loads(raw)
                if not is_customer(user, args.customer_role_id):
                    skipped += 1
                    consumer.commit(msg)
                    continue
                organization, secondary, misses = resolve_orgs(user, cache)
                if misses:
                    print(
                        f"  user {user.get('id')}: {misses} organization(s) not in cache",
                        file=sys.stderr,
                    )
                customer = conform_customer(user, organization, secondary)
                value = avro_serializer(
                    customer, SerializationContext(args.output_topic, MessageField.VALUE)
                )
            except Exception as exc:  # noqa: BLE001 - keep the consumer alive
                failed += 1
                print(
                    f"Skipping bad message at {msg.topic()}[{msg.partition()}]@{msg.offset()}: "
                    f"{type(exc).__name__}: {exc}",
                    file=sys.stderr,
                )
                consumer.commit(msg)
                continue

            producer.produce(
                topic=args.output_topic,
                key=key_serializer(str(customer["id"])),
                value=value,
                on_delivery=_delivery,
            )
            producer.poll(0)
            consumer.commit(msg)
            produced += 1
            if produced % 100 == 0:
                print(f"  ...{produced} customers produced")
    finally:
        producer.flush(10)
        consumer.close()
        cache.close()
        print(f"Done. produced={produced}, skipped_non_customers={skipped}, failed={failed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
