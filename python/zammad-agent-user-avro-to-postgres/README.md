# zammad-agent-user-avro-to-postgres

Consumes Avro **`AgentUser`** records from **`pub.zammad.agent-user.state.v1`**
(Confluent Schema-Registry serialized) and upserts them into the `agent_user` table defined in
[`cr_ticket.sql`](../../scripts/postgresql/cr_ticket.sql).

- Upsert on primary key `id` -> idempotent re-consumption.
- `out_of_office_replacement_id` (self-FK) is written as NULL if the replacement user has not
  been loaded yet.
- Delivery is **at-least-once**: the offset is committed after the PostgreSQL commit. Bad
  messages are logged and skipped.

## Install & run

```bash
python3 -m venv .venv && source .venv/bin/activate.fish
pip install -r requirements.txt
psql "postgresql://postgres:abc123!@localhost:5432/demodb" -f ../../scripts/postgresql/cr_ticket.sql
python zammad_agent_user_avro_to_postgres.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `dataplatform:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://dataplatform:8081` |
| `--input-topic` | `INPUT_TOPIC` | `pub.zammad.agent-user.state.v1` |
| `--group-id` | `GROUP_ID` | `zammad-agent-user-avro-to-postgres` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |
| `--dsn` | `PG_DSN` | `postgresql://postgres:abc123!@dataplatform:5432/demodb` |
| `--pg-schema` | `PG_SCHEMA` | `ticket` |
