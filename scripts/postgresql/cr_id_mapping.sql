-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS ticket;
SET search_path TO ticket;

-- -------------------------------------------------------
-- Zammad ids (BIGINT) <-> data product ids (UUID)
-- -------------------------------------------------------

-- Zammad user_id (ticket.customer_id) <-> Customer id
CREATE TABLE IF NOT EXISTS customer_id_mapping (
    zammad_user_id  BIGINT PRIMARY KEY,
    customer_id     UUID NOT NULL UNIQUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Zammad organization_id <-> Customer organization_id
CREATE TABLE IF NOT EXISTS organization_id_mapping (
    zammad_organization_id  BIGINT PRIMARY KEY,
    organization_id         UUID NOT NULL UNIQUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
