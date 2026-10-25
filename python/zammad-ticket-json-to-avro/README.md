# zammad-json-to-avro

Consumes raw Zammad JSON from the Kafka topic **`priv.zammad.ticket.v1`**, reshapes each
message to the **`TicketAction`** (v1) Avro schema, and produces
Schema-Registry-serialized Avro to an output topic (default
**`pub.zammad.ticket.action.v1`**).

The raw Zammad payload carries far more fields than the schema defines and a few
with different types. The transformation is schema-driven ([schema_conform.py](schema_conform.py)):

- fields not in the Avro schema are **dropped** (`preferences`, `organizations`,
  `daily_event_locks`, `ai_stored_results`, …);
- primitives are **coerced** to the declared Avro type;
- `enum` values are validated against the schema symbols;
- optional `["null", X]` fields default to `null` when absent.

## Schema

The schema is read from the generated Avro file:

```
java/datamesh-meta/target/generated-sources/avro/schema/ticket/action/TicketAction.avsc
```

Generate it first with:

```bash
cd java/datamesh-meta && mvn generate-sources
```

Override the location with `--schema <path>` or `AVRO_SCHEMA=<path>`.

## Install

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Run against Kafka

Defaults match the Platys `dp-kafka-data-mesh` stack (`localhost:9092`,
Schema Registry `http://localhost:8081`):

```bash
python zammad_json_to_avro.py
```

Options (also settable via env vars):

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--input-topic` | `INPUT_TOPIC` | `priv.zammad.ticket.v1` |
| `--output-topic` | `OUTPUT_TOPIC` | `pub.zammad.ticket.action.v1` |
| `--group-id` | `GROUP_ID` | `zammad-json-to-avro` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |

## Test the mapping offline (no Kafka needed)

```bash
# print the conformed record as JSON
python zammad_json_to_avro.py --from-file sample.json

# or write a real Avro container file you can inspect
python zammad_json_to_avro.py --from-file sample.json --to-file out.avro
```
