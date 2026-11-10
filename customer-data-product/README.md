# Data Product `Customer`

First time the necessary scripts/metadata has to be deployed to the Platys stack

```bash
./deploy.sh
```

```bash
cd $DATAPLATFORM_HOME
docker compose up -d datamesh-customer-app
```

## Start the Debezium Outbox Connector

```bash
cd $DATAPLATFORM_HOME
export DATAPLATFORM_IP=dataplatform
../customer-data-product/customer-app/start-outbox.sh
```

## GraphQL (customer-dp)

The read-only GraphQL API lives in the `customer-dp` project and reads the data product schema `customer_dp_v1`
(filled by the Redpanda Connect stream `customer-state-to-postgres`).

```bash
cd $DATAPLATFORM_HOME
docker compose up -d datamesh-customer-dp
```

Navigate to <http://dataplatform:29001/graphiql.html>

Execute a graphql query, such as

```graphql
query Customer {
  customers {
    id
    firstName
    lastName
    active
    verified
    emailAddress
    addresses {
      id
      street
      zipCode
      city
    }
    phones {
      type
      number
    }
  }
}
```

```json
{
    "X-User-Id": "admin",
    "X-User-Roles": "admin"
}
```


## Create the data for the OPA Policy

```bash
cd $DATAPLAFORM_HOME/../customer-data-product/scritps/opt

datacontract export custom \
  --template templates/pii-classification.json.j2 \
  --output rego/pii-classification-data.json \
  ../../meta/odcs/customer-dp-graphql.odcs.yaml
```
