# customer-avro-to-zammad

Two consumers that push data products into Zammad: [`customer_avro_to_zammad.py`](customer_avro_to_zammad.py)
(customers -> users) and [`organization_avro_to_zammad.py`](organization_avro_to_zammad.py) (organizations, see
[below](#organizations)). Both share [mapping.py](mapping.py) and run independently.

## Customers

Consumes Avro **`CustomerState`** records from **`pub.cus.customer.state.v1`**
(Confluent Schema-Registry serialized, conforming to
[`CustomerState-v1.avdl`](../../java/datamesh-meta/src/main/avdl/customer/state/CustomerState-v1.avdl))
and creates the customers in **Zammad** as users with the `Customer` role through the Zammad REST API.

## Mapping

[mapping.py](mapping.py) maps the Avro `Customer` to a Zammad user. Empty strings and nulls are not sent.

| Avro `Customer` | Zammad user |
|-----------------|-------------|
| `login` | `login` |
| `email_address` | `email` |
| `first_name` / `last_name` | `firstname` / `lastname` |
| `phone_number`, `mobile`, `fax`, `web` | `phone`, `mobile`, `fax`, `web` |
| `note`, `department`, `address` | `note`, `department`, `address` |
| `active`, `verified`, `vip` | `active`, `verified`, `vip` |
| `addresses[0]` (`street`, `zip_code`, `city`) | `street`, `zip`, `city` (Zammad keeps one address) |
| - | `roles: ["Customer"]` (on create only) |

Not mapped: `preferred_contact`, `segment`, `image*`, `onboarded_date` (no Zammad counterpart) and
`organization_id`. It is a UUID from the customer system, while Zammad references organizations by its own
integer id or by name.

## Behaviour

- **Upsert on `login`**: the user is searched by login, updated (`PUT`) if it exists and created (`POST`)
  otherwise, so re-consuming a message is idempotent. The role list is only sent on create, so an
  existing user keeps its roles.
- **Id mapping**: after Zammad returned the user id, it is stored with the Customer `id` (UUID) in
  `customer_id_mapping` ([`cr_id_mapping.sql`](../../scripts/postgresql/cr_id_mapping.sql), schema
  `ticket`). Run that script first. The row is upserted on the Zammad user id; a stale row for the same
  customer (for example after a login change created a new Zammad user) is replaced.
- Delivery is **at-least-once**: the offset is committed after Zammad accepted the request and the mapping was committed. Failed
  messages (for example a Zammad validation error) are logged and skipped. Calls are retried up to 3 times on
  429/502/503/504.
- `--dry-run` prints the Zammad payloads instead of calling the API or the database.

## Install & run

Create an API token in Zammad (profile -> Token Access, permission `admin.user`).

```bash
python3 -m venv .venv && source .venv/bin/activate.fish
pip install -r requirements.txt
psql "postgresql://demo:abc123!@localhost:5432/demodb" -f ../../scripts/postgresql/cr_id_mapping.sql
export ZAMMAD_TOKEN=<token>
python customer_avro_to_zammad.py
```

| Flag | Env | Default |
|------|-----|---------|
| `--bootstrap-servers` | `BOOTSTRAP_SERVERS` | `dataplatform:9092` |
| `--schema-registry-url` | `SCHEMA_REGISTRY_URL` | `http://dataplatform:8081` |
| `--input-topic` | `INPUT_TOPIC` | `pub.cus.customer.state.v1` |
| `--group-id` | `GROUP_ID` | `customer-avro-to-zammad` |
| `--offset-reset` | `OFFSET_RESET` | `earliest` |
| `--zammad-url` | `ZAMMAD_URL` | `http://dataplatform:8080` |
| `--zammad-token` | `ZAMMAD_TOKEN` | - |
| `--zammad-user` / `--zammad-password` | `ZAMMAD_USER` / `ZAMMAD_PASSWORD` | - (basic auth, if no token) |
| `--dsn` | `PG_DSN` | `postgresql://demo:abc123!@dataplatform:5432/demodb` |
| `--pg-schema` | `PG_SCHEMA` | `ticket` |
| `--dry-run` | - | off (skips Zammad and the database) |

## Organizations

[`organization_avro_to_zammad.py`](organization_avro_to_zammad.py) consumes Avro **`OrganizationState`** records from
**`pub.cus.organization.state.v1`** (conforming to
[`OrganizationState-v1.avdl`](../../java/datamesh-meta/src/main/avdl/organisation/state/OrganizationState-v1.avdl))
and creates the organizations in Zammad (`/api/v1/organizations`).

| Avro `Organization` | Zammad organization |
|---------------------|---------------------|
| `name` | `name` |
| `shared`, `domain_assignment`, `active`, `vip` | `shared`, `domain_assignment`, `active`, `vip` |
| `domain` | `domain` (sent even when empty) |
| `note` | `note` (omitted when null) |

Not mapped: `member_ids` (Zammad organizations get their members from the users) and `created_at` / `updated_at`.

- **Id mapping**: the Zammad organization id is stored with the Organization `id` (UUID) in
  `organization_id_mapping` (schema `ticket`, same script `cr_id_mapping.sql`).
- **Upsert**: the Zammad id is looked up in the mapping table first, so a renamed organization is updated instead of
  duplicated. Without a mapping row it is matched by name, then created if missing.
- Same delivery guarantee, retries and `--dry-run` as the customer consumer.

```bash
python organization_avro_to_zammad.py
```

Flags and environment variables are the same as in the table above, except:

| Flag | Env | Default |
|------|-----|---------|
| `--input-topic` | `INPUT_TOPIC` | `pub.cus.organization.state.v1` |
| `--group-id` | `GROUP_ID` | `organization-avro-to-zammad` |

The customer consumer does not link users to these organizations yet. The mapping table now has what it needs for that:
`customer.organization_id` (UUID) -> `organization_id_mapping` -> Zammad organization id.
