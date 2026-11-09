-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS customer_dp_v1;
SET search_path TO customer_dp_v1;


-- 3) Customer (Customer record, Customer.avdl).
--    Segment is stored denormalized as a plain value (no lookup table).
CREATE TABLE IF NOT EXISTS customer (
    id                 TEXT        PRIMARY KEY,
    login              TEXT        NOT NULL,
    email_address      TEXT        NOT NULL,
    first_name         TEXT,
    last_name          TEXT        NOT NULL,
    preferred_contact  TEXT,
    segment            TEXT,                               -- e.g. bronze, silver, gold, platinum
    date_of_birth      DATE,
    preferred_language TEXT,
    marketing_consent  BOOLEAN     NOT NULL DEFAULT false,
    lifecycle_status   TEXT,
    image              TEXT,
    image_source       TEXT,
    department         TEXT,
    active             BOOLEAN     NOT NULL,
    verified           BOOLEAN     NOT NULL,
    vip                BOOLEAN     NOT NULL,
    web                TEXT        NOT NULL DEFAULT '',
    note               TEXT        NOT NULL DEFAULT '',
    address            TEXT,
    onboarded_date     TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4) Address (Address record, array in Customer.addresses).
--    Country and type are stored denormalized as plain values.
CREATE TABLE IF NOT EXISTS address (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id TEXT   NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    street      TEXT,
    zip_code    TEXT,
    city        TEXT   NOT NULL,
    state       TEXT,
    country     TEXT,                                      -- ISO 3166-1 alpha-2 code
    type        TEXT,                                      -- e.g. BILLING, SHIPPING, HOME
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) Phone (Phone record, array in Customer.phones).
--    Type is stored denormalized as a plain value.
CREATE TABLE IF NOT EXISTS phone (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  TEXT   NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    number       TEXT   NOT NULL,
    type         TEXT,                                     -- e.g. MOBILE, HOME, WORK, FAX
    availability TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_address_customer ON address (customer_id);
CREATE INDEX IF NOT EXISTS idx_phone_customer   ON phone   (customer_id);

COMMENT ON TABLE customer IS 'One row per customer, mirroring the Customer Avro record.';
COMMENT ON TABLE address  IS 'Customer addresses (Customer.addresses array).';
COMMENT ON TABLE phone    IS 'Customer phone numbers (Customer.phones array).';
