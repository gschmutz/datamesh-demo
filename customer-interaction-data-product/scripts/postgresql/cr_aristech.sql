-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS aristech;
SET search_path TO aristech;

