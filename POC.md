# Kafka DataMesh PoC

```bash
docker exec -ti kafka-1 kafka-topics --create \
    --bootstrap-server kafka-1:19092 \
    --topic demo.products \
    --replication-factor 3 --partitions 6 \
    --config cleanup.policy=compact \
    --config segment.ms=100 \
    --config delete.retention.ms=100 \
    --config min.cleanable.dirty.ratio=0.001 \
    --if-not-exists

docker exec -ti kafka-1 kafka-topics --create \
    --bootstrap-server kafka-1:19092 \
    --topic demo.purchases \
    --replication-factor 3 --partitions 6 \
    --if-not-exists

docker exec -ti kafka-1 kafka-topics --create \
    --bootstrap-server kafka-1:19092 \
    --topic demo.inventories \
    --replication-factor 3 --partitions 6 \
    --if-not-exists
```

Start the simulator with:

<https://github.com/TrivadisPF/various-bigdata-prototypes/tree/master/streaming-sources/sales-simulator>

```bash
docker run -ti --rm --network platys-platform \
    -e KAFKA_BOOTSTRAP_SERVERS=kafka-1:19092,kafka-2:19093 -e KAFKA_SCHEMA_REGISTRY_URL=http://schema-registry-1:8081 -e KAFKA_MESSAGE_FORMAT=avro \
    trivadis/sales-simulator:latest
```    

```bash
kcat -b dataplatform:9092 -t demo.purchases -q -f 'Part-%p => %k:%s\n'
```

## Zammad to Kafka

Configure vector to listen on HTTP and send to Kafka

```yaml
# Vector configuration
# See https://vector.dev/docs/reference/configuration/ for all options

# Data directory for internal state (used when VECTOR_volume_map_data: true)
data_dir: /var/lib/vector

# API (used for the health check endpoint on port 9598)
api:
  enabled: true
  address: "0.0.0.0:9598"

sources:
  zammad_ticket_in:
    type: http_server
    address: "0.0.0.0:28730"
    path: /ticket
    encoding: json

  zammad_customer_in:
    type: http_client
    endpoint: "http://2.58.54.30:28425/api/v1/users/search?query=created_at:%3Enow-1h&sort_by=id&order_by=desc"
    method: GET
    scrape_interval_secs: 60
    scrape_timeout_secs: 10
    auth:
      strategy: basic
      user: "schmutz68@gmail.com"
      password: "Abc123!Abc123!"
    headers:
      Accept:
        - application/json
    decoding:
      codec: json

  aristech_in:
    type: http_server
    address: "0.0.0.0:28731"
    encoding: json  

  internal_metrics:
    type: internal_metrics
    scrape_interval_secs: 10

transforms:  
  deduplicate_by_id:
    type: dedupe
    inputs:
      - zammad_customer_in
    fields:
      match:
        - id

  aristech_parse_message:
    type: remap
    inputs:
      - aristech_in
    source: |
      . = parse_json!(.message)

sinks:
  zammad_ticket_out:
    type: kafka
    inputs:
      - aristech_in
    bootstrap_servers: "${KAFKA_BOOTSTRAP_SERVERS}"
    topic: priv.zammad.ticket.v1
    encoding:
      codec: json
  
  zammad_customer_out:
    type: kafka
    inputs:
      - deduplicate_by_id
    bootstrap_servers: "${KAFKA_BOOTSTRAP_SERVERS}"
    topic: priv.zammad.customer.v1
    key_field: "id"
    encoding:
      codec: json

  aristech_out:
    type: kafka
    inputs:
      - aristech_parse_message
    bootstrap_servers: "${KAFKA_BOOTSTRAP_SERVERS}"
    encoding:
      codec: json
    topic: priv.aristech.v1

  prometheus:
    type: prometheus_exporter
    inputs:
      - internal_metrics
    address: 0.0.0.0:9090      
```

```avdl
@namespace("ch.admin.zammad")
protocol ZammadTicketProtocol {

  // -------------------------------------------------------
  // Enums
  // -------------------------------------------------------

  enum SenderType {
    Customer, Agent, System
  }

  enum ArticleType {
    email, phone, web, note, sms, chat, fax
  }

  enum ContentType {
    `text/html`,
    `text/plain`
  }

  enum FollowUpPossible {
    yes, no, new_ticket
  }

  enum SummaryGeneration {
    global_default, enabled, disabled
  }

  // -------------------------------------------------------
  // Reusable Records
  // -------------------------------------------------------

  record User {
    long   id;
    string login;
    string email;
    string firstname;
    string lastname;
    union { null, string } image        = null;
    union { null, string } image_source = null;
    union { null, string } department   = null;
    union { null, string } organization_id = null;
    array<long>   organization_ids      = [];
    array<string> role_ids              = [];
    array<string> roles                 = [];
    string  created_at;
    string  updated_at;
    string  created_by;
    string  updated_by;
    long    created_by_id;
    long    updated_by_id;
    boolean active;
    boolean verified;
    boolean vip;
    boolean out_of_office;
    union { null, string } out_of_office_start_at      = null;
    union { null, string } out_of_office_end_at        = null;
    union { null, long }   out_of_office_replacement_id = null;
    string phone   = "";
    string mobile  = "";
    string fax     = "";
    string web     = "";
    string street  = "";
    string city    = "";
    string zip     = "";
    string country = "";
    string note    = "";
    union { null, string } address = null;
    union { null, string } source  = null;
  }

  record Group {
    long   id;
    string name;
    string name_last;
    union { null, string } note              = null;
    union { null, long }   email_address_id  = null;
    union { null, long }   parent_id         = null;
    union { null, long }   assignment_timeout = null;
    union { null, long }   reopen_time_in_days = null;
    boolean active;
    boolean follow_up_assignment;
    FollowUpPossible follow_up_possible;
    boolean shared_drafts;
    string  signature;
    long    signature_id;
    SummaryGeneration summary_generation;
    array<long>   user_ids = [];
    array<string> users    = [];
    string created_at;
    string updated_at;
    string created_by;
    string updated_by;
    long   created_by_id;
    long   updated_by_id;
  }

  record Priority {
    long   id;
    string name;
    union { null, string } note     = null;
    union { null, string } ui_color = null;
    union { null, string } ui_icon  = null;
    boolean default_create;
    boolean active;
    string  created_at;
    string  updated_at;
    string  created_by;
    string  updated_by;
    long    created_by_id;
    long    updated_by_id;
  }

  // -------------------------------------------------------
  // Article
  // -------------------------------------------------------

  record Article {
    long   id;
    long   ticket_id;
    string body;
    ContentType content_type;
    ArticleType type;
    long        type_id;
    SenderType  sender;
    long        sender_id;
    string      from;
    string      to;
    boolean     internal;
    boolean     body_rendering_error;
    union { null, string } subject          = null;
    union { null, string } cc               = null;
    union { null, string } reply_to         = null;
    union { null, string } in_reply_to      = null;
    union { null, string } message_id       = null;
    union { null, string } message_id_md5   = null;
    union { null, string } origin_by        = null;
    union { null, long }   origin_by_id     = null;
    union { null, string } detected_language = null;
    union { null, string } time_unit        = null;
    double accounted_time                   = 0.0;
    User   created_by;
    long   created_by_id;
    User   updated_by;
    long   updated_by_id;
    string created_at;
    string updated_at;
    array<string> attachments       = [];
    array<string> ai_stored_result_ids = [];
  }

  // -------------------------------------------------------
  // Ticket
  // -------------------------------------------------------

  record Ticket {
    long   id;
    string number;
    string title;
    string state;
    long   state_id;
    long   group_id;
    Group  group;
    long   priority_id;
    Priority priority;
    long   owner_id;
    User   owner;
    long   customer_id;
    User   customer;
    union { null, long }   organization_id = null;
    long   create_article_sender_id;
    string create_article_sender;
    long   create_article_type_id;
    string create_article_type;
    long   article_count;
    array<long> article_ids = [];
    union { null, string } note           = null;
    union { null, string } type           = null;
    union { null, string } pending_time   = null;
    union { null, string } close_at       = null;
    union { null, string } escalation_at  = null;
    union { null, string } first_response_at           = null;
    union { null, string } first_response_escalation_at = null;
    union { null, string } close_escalation_at         = null;
    union { null, string } update_escalation_at        = null;
    union { null, string } last_contact_at             = null;
    union { null, string } last_contact_agent_at       = null;
    union { null, string } last_contact_customer_at    = null;
    union { null, string } last_owner_update_at        = null;
    union { null, string } last_close_at               = null;
    union { null, long }   first_response_in_min       = null;
    union { null, long }   first_response_diff_in_min  = null;
    union { null, long }   close_in_min                = null;
    union { null, long }   close_diff_in_min           = null;
    union { null, long }   update_in_min               = null;
    union { null, long }   update_diff_in_min          = null;
    union { null, long }   time_unit                   = null;
    union { null, long }   checklist_id                = null;
    boolean ai_agent_running                           = false;
    string  created_at;
    string  updated_at;
    User    created_by;
    long    created_by_id;
    User    updated_by;
    long    updated_by_id;
    array<string> ai_stored_result_ids = [];
  }

  // -------------------------------------------------------
  // Top-level envelope (webhook payload)
  // -------------------------------------------------------

  record ZammadTicketEvent {
    Ticket  ticket;
    Article article;
  }
}
```



## Kafka Connect CDC to PostgreSQL

```bash
curl -X PUT \
  "http://dataplatform:8083/connectors/products.jdbcsnk/config" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
  "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
  "tasks.max": "1",
  "connection.url": "jdbc:postgresql://postgresql:5432/postgres",
  "connection.user": "postgres",
  "connection.password": "abc123!",

  "topics": "demo.products",

  "insert.mode": "upsert",
  "pk.mode": "record_key",
  "pk.fields": "product_id",

  "auto.create": "true",
  "auto.evolve": "true",

  "table.name.format": "products",

  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter": "io.confluent.connect.avro.AvroConverter",
  "value.converter.schema.registry.url": "http://schema-registry-1:8081"  
}'
```

```bash
curl -X PUT \
  "http://dataplatform:8083/connectors/purchases.jdbcsnk/config" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
  "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
  "tasks.max": "1",
  "connection.url": "jdbc:postgresql://postgresql:5432/postgres",
  "connection.user": "postgres",
  "connection.password": "abc123!",

  "topics": "demo.purchases",

  "insert.mode": "upsert",
  "pk.mode": "record_key",
  "pk.fields": "transaction_id",

  "auto.create": "true",
  "auto.evolve": "true",

  "table.name.format": "purchases",

  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter": "io.confluent.connect.avro.AvroConverter",
  "value.converter.schema.registry.url": "http://schema-registry-1:8081"  
}'
```

## Zammad 

```bash
curl  -u schmutz68@gmail.com:Abc123!Abc123! -X POST "http://2.58.54.30:28425/api/v1/tickets" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Something broke in production",
    "group": "Users",
    "customer_id": "guess:customer@example.com",
    "article": {
      "subject": "Production issue",
      "body": "Details of the issue...",
      "type": "note",
      "sender": "Customer",
      "internal": false
    }
  }'
```  