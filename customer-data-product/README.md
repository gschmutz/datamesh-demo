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

## GraphQL 

Navigate to <http://2.58.54.30:29000/graphiql.html>

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




