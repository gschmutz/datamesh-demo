# customer-graphql

Spring Boot service that exposes the PostgreSQL **customer** schema
([`cr_customer.sql`](../../scripts/postgresql/cr_customer.sql)) over **Spring for
GraphQL**.

- **Spring Boot 3.3**, **Spring for GraphQL**, **Spring Data JPA**, PostgreSQL.
- JPA entities map the tables and their relationships:
  - `customer` → [`Customer`](src/main/java/ch/admin/customer/graphql/domain/Customer.java)
  - `organization` → [`Organization`](src/main/java/ch/admin/customer/graphql/domain/Organization.java)
  - `customer.organization_id` → `@ManyToOne` (primary org)
  - `customer_secondary_organization` → `@ManyToMany` (secondary orgs)
  - `member_ids` / `secondary_member_ids` (`BIGINT[]`) → `List<Long>` via Hibernate array mapping
- GraphQL schema: [`schema.graphqls`](src/main/resources/graphql/schema.graphqls),
  with `DateTime` and `Long` scalars (graphql-java-extended-scalars) for the
  timestamps and id arrays.

## Prerequisites

The `customer` schema and tables must exist (in `demodb`):

```bash
psql "postgresql://demo:abc123!@localhost:5432/demodb" -f ../../scripts/postgresql/cr_customer.sql
```

## Run

Requires JDK 17+ (built/tested with 21). The service connects to the `customer`
schema via `currentSchema` + Hibernate `default_schema`.

```bash
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

Connection is configurable via env vars (defaults match the Platys stack):

| Env | Default |
|-----|---------|
| `PG_HOST` | `localhost` |
| `PG_PORT` | `5432` |
| `PG_DB` | `demodb` |
| `PG_USER` | `demo` |
| `PG_PASSWORD` | `abc123!` |
| `PG_SCHEMA` | `customer` |
| `SERVER_PORT` | `8088` |

## Endpoints

- GraphQL: `POST http://localhost:8088/graphql`
- GraphiQL (interactive IDE): `http://localhost:8088/graphiql`

## Queries

```graphql
{
  customerById(id: 7) {
    id login firstname lastname createdAt
    organization { id name domain memberIds }
    secondaryOrganizations { id name note }
  }
  customers(limit: 50) { id login }
  organizations(limit: 50) { id name domain }
  organizationById(id: 2) { id name memberIds secondaryMemberIds }
}
```

Also available: `customerByLogin(login: "...")`.
