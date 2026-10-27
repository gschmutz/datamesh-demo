-- =======================================================================
-- Customer data product schema
-- Models the data described by:
--   src/main/avdl/customer/Customer.avdl      (record Customer)
--   src/main/avdl/customer/Organization.avdl  (record Organization, embedded)
--
-- The Avro Customer embeds a primary `organization` and an array of
-- `secondary_organizations`; those are normalized here into an `organization`
-- table, a primary-org FK on `customer`, and a `customer_secondary_organization`
-- join table (M:N).
--
-- -----------------------------------------------------------------------
-- Placement when used as a PostgreSQL init script
-- (/docker-entrypoint-initdb.d): the entrypoint runs each *.sql against
-- POSTGRES_DB as POSTGRES_USER, and unqualified objects go into `public`.
-- To force a dedicated DATABASE and SCHEMA we therefore:
--   1. \connect to the target database (the platform ships `demodb`);
--   2. CREATE SCHEMA and SET search_path so the tables below land in it.
-- Adjust the database name, schema name and grant role to your setup.
-- (Name the file e.g. 10_cr_customer.sql so it runs AFTER the database is
--  created by 00_create-muliple-postgresql-databases.sh.)
-- =======================================================================

-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS customer;
SET search_path TO customer;

-- -------------------------------------------------------
-- Organization  (Customer.organization / .secondary_organizations)
-- -------------------------------------------------------
CREATE TABLE organization (
    id                      BIGINT PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    shared                  BOOLEAN NOT NULL,
    domain                  VARCHAR(255) NOT NULL DEFAULT '',
    domain_assignment       BOOLEAN NOT NULL,
    active                  BOOLEAN NOT NULL,
    vip                     BOOLEAN NOT NULL,
    note                    TEXT,                               -- nullable
    -- Avro arrays of member ids kept as native integer arrays.
    member_ids              BIGINT[] NOT NULL DEFAULT '{}',
    secondary_member_ids    BIGINT[] NOT NULL DEFAULT '{}',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

-- -------------------------------------------------------
-- Customer  (Zammad user in the Customer role)
-- -------------------------------------------------------
CREATE TABLE customer (
    id              BIGINT PRIMARY KEY,
    login           VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    firstname       VARCHAR(128) NOT NULL,
    lastname        VARCHAR(128) NOT NULL,
    image           VARCHAR(64),                        -- md5 hash ref, nullable
    image_source    TEXT,                               -- nullable
    department      VARCHAR(255),                       -- nullable
    -- Primary organization (Customer.organization, nullable union in Avro).
    organization_id BIGINT REFERENCES organization(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    active          BOOLEAN NOT NULL,
    verified        BOOLEAN NOT NULL,
    vip             BOOLEAN NOT NULL,
    phone           VARCHAR(64)  NOT NULL DEFAULT '',
    mobile          VARCHAR(64)  NOT NULL DEFAULT '',
    fax             VARCHAR(64)  NOT NULL DEFAULT '',
    web             VARCHAR(255) NOT NULL DEFAULT '',
    street          VARCHAR(255) NOT NULL DEFAULT '',
    city            VARCHAR(128) NOT NULL DEFAULT '',
    zip             VARCHAR(32)  NOT NULL DEFAULT '',
    country         VARCHAR(128) NOT NULL DEFAULT '',
    note            TEXT         NOT NULL DEFAULT '',
    address         TEXT                                -- nullable
);

-- -------------------------------------------------------
-- Customer <-> secondary organizations  (M:N)
-- (Customer.secondary_organizations)
-- -------------------------------------------------------
CREATE TABLE customer_secondary_organization (
    customer_id     BIGINT NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    PRIMARY KEY (customer_id, organization_id)
);

-- -------------------------------------------------------
-- Indexes
-- -------------------------------------------------------
CREATE INDEX idx_customer_organization ON customer(organization_id);
CREATE INDEX idx_customer_email        ON customer(email);
CREATE INDEX idx_cust_sec_org_org      ON customer_secondary_organization(organization_id);

-- -------------------------------------------------------
-- Grants for the application role (the platform ships `demo`).
-- Remove/adjust if your app connects as the owner.
-- -------------------------------------------------------
GRANT USAGE ON SCHEMA customer TO demo;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA customer TO demo;
ALTER DEFAULT PRIVILEGES IN SCHEMA customer
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO demo;
