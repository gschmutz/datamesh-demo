# zammad-user-json-to-avro

Consumes raw Zammad user JSON from **`priv.zammad.user.v1`**, reshapes each message to the
**`AgentUser`** Avro schema ([AgentUser.avdl](../../java/datamesh-meta/src/main/avdl/ticket/AgentUser.avdl))
and produces Schema-Registry-serialized Avro to **`pub.zammad.agent-user.state.v1`**
(keyed by the Zammad user id).

Transformation is schema-driven ([schema_conform.py](schema_conform.py)): unknown fields
(`role_ids`, `organization_ids`, `created_by*`, `preferences`, ...) are dropped, primitives
coerced, missing optional fields default to `null`.

## Filtering

Evaluated on the raw JSON (the schema no longer has `role_ids`). Only users whose `role_ids` contain role id **2** are converted; others are dropped (their
offsets are still committed). Change with `--role-id` / `ROLE_ID`.

## Schema

Generate the `.avsc` first (default `target/generated-sources/avro/schema/ticket/AgentUser.avsc`,
otherwise searched recursively):

```bash
cd java/datamesh-meta && mvn generate-sources
```

Override with `--schema <path>` or `AVRO_SCHEMA`.

## Install & run

```bash
python3 -m venv .venv && source .venv/bin/activate.fish
pip install -r requirements.txt
python3 zammad_user_json_to_avro.py
```

Options (flag / env): `--bootstrap-servers` / `BOOTSTRAP_SERVERS` (`dataplatform:9092`),
`--schema-registry-url` / `SCHEMA_REGISTRY_URL` (`http://dataplatform:8081`),
`--input-topic` / `INPUT_TOPIC`, `--output-topic` / `OUTPUT_TOPIC`, `--group-id` / `GROUP_ID`,
`--offset-reset` / `OFFSET_RESET`.

## Offline test

```bash
python zammad_user_json_to_avro.py --from-file sample.json
python zammad_user_json_to_avro.py --from-file sample.json --to-file out.avro
```
