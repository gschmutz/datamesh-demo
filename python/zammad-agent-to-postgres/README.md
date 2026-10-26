# zammad-customer-to-postgres

Consumes Avro **Customer** records from `pub.zammad.customer.v1` (Confluent
Schema-Registry serialized, conforming to
[`Customer.avdl`](../../java/datamesh-meta/src/main/avdl/customer/Customer.avdl))
and loads them into the normalized PostgreSQL schema defined in
[`cr_customer.sql`](../../scripts/postgresql/cr_customer.sql).

## Mapping

The Avro `Customer` embeds a primary `organization` and a list of
`secondary_organizations`; [mapping.py](mapping.py) normalizes them, upserting
by primary key (idempotent):

| Avro | PostgreSQL |
|------|------------|
| `organization`, each of `secondary_organizations` | `organization` (one row per org) |
| `organization.id` | `customer.organization_id` (FK to primary org, nullable) |
| `secondary_organizations` | `customer_secondary_organization` (M:N join, rebuilt per message) |
| scalar Customer fields | `customer` |

- Organizations are written **first** (FK targets), then the customer, then its
  secondary-org join rows (deleted + reinserted so updates are reflected).
- `member_ids` / `secondary_member_ids` are stored as PostgreSQL `BIGINT[]`.
- ISO-8601 timestamp strings are parsed to `timestamptz`.
- Delivery is **at-least-once**: the Kafka offset is committed only after the DB
  transaction commits.

## Prerequisites

Create the schema/tables (and the `customer` schema) first:

```bash
psql "postgresql://demo:abc123!@localhost:5432/demodb" -f ../../scripts/postgresql/cr_customer.sql
```

## Install & run

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python customer_to_postgres.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--input-topic` | `INPUT_TOPIC` | `pub.zammad.customer.v1` |
| `--group-id` | `GROUP_ID` | `zammad-customer-to-postgres` |
| `--dsn` | `PG_DSN` | `postgresql://demo:abc123!@localhost:5432/demodb` |
| `--db-schema` | `DB_SCHEMA` | `customer` |

The app sets `search_path` to `--db-schema` on connect, so it writes into the
`customer` schema created by `cr_customer.sql`.
