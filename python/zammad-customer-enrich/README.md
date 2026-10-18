# zammad-customer-enrich

Builds the **Customer** data product from the Zammad user and organization
streams and publishes it as Avro.

```
priv.zammad.organization.v1 (JSON) ──▶ in-memory org lookup cache (id ▶ Organization)
priv.zammad.user.v1 (JSON) ──▶ filter role_ids∋3 ──▶ enrich ──▶ Avro ──▶ pub.zammad.customer.v1
```

- **Consume** raw JSON users from `priv.zammad.user.v1`.
- **Filter** to users whose `role_ids` contains **3** (the customer role).
- **Enrich** from an organization cache built from `priv.zammad.organization.v1`:
  - `organization` ← the user's `organization_id` (primary org, full record)
  - `secondary_organizations` ← the user's `organization_ids` (the rest; primary excluded)
  - org ids not in the cache are skipped and logged as misses.
- **Serialize** to Avro using the generated schema
  `customer/Customer.avsc` + Confluent Schema Registry, and **produce** to
  `pub.zammad.customer.v1` (keyed by customer id).

Delivery is **at-least-once**: the user-topic offset is committed only after the
customer record is produced.

## Organization cache

[org_cache.py](org_cache.py) reads the organization topic from the **beginning**
on every start (manual partition assignment, no consumer group) so the lookup
table is always fully rebuilt, then keeps following the topic for live updates in
a background thread. The main loop waits until the initial load has caught up
before processing users.

> If a user is processed before its organization has been loaded, that org is
> reported as a miss. The startup warm-up makes this rare; organizations added
> after warm-up are picked up by the background follower.

## Schema

Generate it first:

```bash
cd ../../java/datamesh-meta && mvn generate-sources
```

Path: `java/datamesh-meta/target/generated-sources/avro/schema/customer/Customer.avsc`
(override with `--schema` or `CUSTOMER_SCHEMA`).

## Install & run

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python customer_enrich.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--user-topic` | `USER_TOPIC` | `priv.zammad.user.v1` |
| `--organization-topic` | `ORGANIZATION_TOPIC` | `priv.zammad.organization.v1` |
| `--output-topic` | `OUTPUT_TOPIC` | `pub.zammad.customer.v1` |
| `--group-id` | `GROUP_ID` | `zammad-customer-enrich` |
| `--customer-role-id` | `CUSTOMER_ROLE_ID` | `3` |
