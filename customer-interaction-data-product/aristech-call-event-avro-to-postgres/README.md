# aristech-avro-to-postgres

Consumes Avro **`CallEvent`** records from the Kafka topic
**`pub.aristech.call.event.v1`** (Confluent Schema-Registry serialized) and loads them into the
PostgreSQL table `aristech.call_event` defined in
[`cr_aristech.sql`](../scripts/postgresql/cr_aristech.sql).

[mapping.py](mapping.py) flattens the nested `userData` record into columns and
**upserts on `call_id`**, so re-consuming the same or an updated event is idempotent.

## How the mapping works

| Source (Avro) | Column in `call_event` |
|---------------|------------------------|
| `callID` | `call_id` (primary key) |
| `isAuthenticated` | `is_authenticated` |
| `isIdentified` | `is_identified` |
| `userData.anliegen` | `anliegen` |
| `userData.bedrohung` | `bedrohung` |
| `userData.kundennummer` | `kundennummer` (nullable) |
| `userData.plz` | `plz` (nullable) |
| `userData.prio` | `prio` |
| `userData.socL` | `soc_l` |

`created_at` is filled by the database default.

Delivery is **at-least-once**: the Kafka offset is committed only after the
PostgreSQL transaction for that message commits. Messages that fail are logged
and their offset is committed so a poison message doesn't block the topic.

## Install

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Create the table

```bash
psql "postgresql://demo:abc123!@localhost:5432/demodb" -f ../scripts/postgresql/cr_aristech.sql
```

## Run

Defaults match the Platys `dp-kafka-data-mesh` stack:

```bash
python aristech_avro_to_postgres.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--input-topic` | `INPUT_TOPIC` | `pub.aristech.call.event.v1` |
| `--group-id` | `GROUP_ID` | `aristech-avro-to-postgres` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |
| `--dsn` | `PG_DSN` | `postgresql://demo:abc123!@localhost:5432/demodb` |
| `--pg-schema` | `PG_SCHEMA` | `aristech` |
