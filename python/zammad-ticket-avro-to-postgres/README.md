# zammad-avro-to-postgres

Consumes Avro **`ZammadTicketState`** records from the Kafka topic
**`pub.zammad`** (Confluent Schema-Registry serialized) and loads them into the
normalized PostgreSQL schema defined in [`cr_schema.sql`](../../scripts/postgresql/cr_schema.sql).

Each Avro message is a denormalized `{ticket, article}` tree with embedded
user / group / priority records. [mapping.py](mapping.py) fans it out into the
relational tables and **upserts on primary key**, so re-consuming the same or an
updated message is idempotent.

## How the mapping works

| Source (Avro) | Target table(s) |
|---------------|-----------------|
| `ticket.owner`, `.customer`, `.created_by`, `.updated_by`, `article.created_by`, `.updated_by` | `agent_user` |
| user `role_ids` + `roles` | `role`, `user_role` |
| `ticket.priority` | `priority` |
| `ticket.group` | `ticket_group` |
| `ticket` | `ticket` |
| `article` (+ `type`/`sender` enums, `create_article_*` on ticket) | `article`, `article_type`, `article_sender` |

Foreign-key safety:

- **User self-references** (`created_by_id`, `updated_by_id`,
  `out_of_office_replacement_id`) are resolved in two phases: users are upserted
  first with those columns NULL, then a second pass sets them once every user
  exists.
- Any user/group FK pointing at an id not received as a full record is written
  as **NULL** instead of violating the constraint.
- The Avro schema carries **no organization record**, so `ticket.organization_id`
  is left NULL and `user_organization` is not populated.
- `article.from` / `article.to` map to `from_address` / `to_address`
  (the SQL columns, avoiding reserved words).

Delivery is **at-least-once**: the Kafka offset is committed only after the
PostgreSQL transaction for that message commits.

## Install

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Create the tables

```bash
psql "postgresql://demo:abc123!@localhost:5432/demodb" -f ../../scripts/postgresql/cr_schema.sql
```

## Run

Defaults match the Platys `dp-kafka-data-mesh` stack:

```bash
python zammad_avro_to_postgres.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--input-topic` | `INPUT_TOPIC` | `pub.zammad` |
| `--group-id` | `GROUP_ID` | `zammad-avro-to-postgres` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |
| `--dsn` | `PG_DSN` | `postgresql://demo:abc123!@localhost:5432/demodb` |
