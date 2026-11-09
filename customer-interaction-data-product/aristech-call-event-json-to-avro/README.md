# aristech-json-to-avro

Consumes raw Aristech JSON from the Kafka topic **`priv.aristech.v1`**, reshapes each
message to the **`CallEvent`** Avro schema
([CallEvent.avdl](../meta/src/main/avdl/aristech/event/CallEvent.avdl)), and produces
Schema-Registry-serialized Avro to **`pub.aristech.call.event.v1`**.

The transformation is schema-driven ([schema_conform.py](schema_conform.py)):

- fields not in the Avro schema are **dropped**;
- primitives are **coerced** to the declared Avro type (e.g. a numeric `prio` becomes a string);
- optional `["null", X]` fields (`kundennummer`, `plz`) default to `null` when absent;
- a missing required field (e.g. `callID`) causes the message to be skipped and logged.

## Schema

The schema is read from the generated Avro file:

```
meta/target/generated-sources/avro/schema/aristech/event/CallEvent.avsc
```

Generate it first with:

```bash
cd meta && mvn generate-sources
```

Override the location with `--schema <path>` or `AVRO_SCHEMA=<path>`.

## Install

```bash
python3 -m venv .venv && source .venv/bin/activate.fish
pip install -r requirements.txt
```

## Run against Kafka

Defaults match the Platys `dp-kafka-data-mesh` stack (`localhost:9092`,
Schema Registry `http://localhost:8081`):

```bash
python3 aristech_json_to_avro.py
```

Options (also settable via env vars):

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `localhost:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://localhost:8081` |
| `--input-topic` | `INPUT_TOPIC` | `priv.aristech.v1` |
| `--output-topic` | `OUTPUT_TOPIC` | `pub.aristech.call.event.v1` |
| `--group-id` | `GROUP_ID` | `aristech-json-to-avro` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |

## Test the mapping offline (no Kafka needed)

```bash
# print the conformed record as JSON
python aristech_json_to_avro.py --from-file sample.json

# or write a real Avro container file you can inspect
python aristech_json_to_avro.py --from-file sample.json --to-file out.avro
```

[sample.json](sample.json) contains an example raw Aristech payload.
