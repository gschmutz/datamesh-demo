-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS aristech;
SET search_path TO aristech;

-- 3) Call table: one row per call handled by the Aristech AI-Suite
--    (CallEvent record, with the nested userData record flattened into columns).
CREATE TABLE IF NOT EXISTS call_event (
    call_id          TEXT        PRIMARY KEY,               -- callID
    is_authenticated BOOLEAN     NOT NULL,                  -- isAuthenticated
    is_identified    BOOLEAN     NOT NULL,                  -- isIdentified
    anliegen         TEXT        NOT NULL,                  -- userData.anliegen: reason for the call
    bedrohung        BOOLEAN     NOT NULL,                  -- userData.bedrohung: threat detected/reported
    kundennummer     TEXT,                                  -- userData.kundennummer (alphanumeric, nullable)
    plz              TEXT,                                  -- userData.plz (string to preserve leading zeros, nullable)
    prio             TEXT        NOT NULL,                  -- userData.prio: numeric string, scale TBC
    soc_l            BOOLEAN     NOT NULL,                  -- userData.socL: unknown meaning (TBC with producer)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()     -- row insertion time (not part of the event)
);

COMMENT ON TABLE  call_event                  IS 'One row per call handled by the Aristech AI-Suite.';
COMMENT ON COLUMN call_event.call_id          IS 'Identifier of the call as assigned by the Aristech AI-Suite.';
COMMENT ON COLUMN call_event.is_authenticated IS 'Whether the caller was successfully authenticated during the call.';
COMMENT ON COLUMN call_event.is_identified    IS 'Whether the caller could be matched to a known customer.';
COMMENT ON COLUMN call_event.anliegen         IS 'Free-text or classified reason for the call.';
COMMENT ON COLUMN call_event.bedrohung        IS 'Flag indicating that a threat was detected or reported in the call.';
COMMENT ON COLUMN call_event.kundennummer     IS 'Customer number as stated by or resolved for the caller. Alphanumeric.';
COMMENT ON COLUMN call_event.plz              IS 'Postal code of the caller. Kept as string to preserve leading zeros.';
COMMENT ON COLUMN call_event.prio             IS 'Priority assigned to the call, transmitted as a numeric string. Scale and direction TBC.';
COMMENT ON COLUMN call_event.soc_l            IS 'Boolean flag with unknown meaning (TBC with producer).';
COMMENT ON COLUMN call_event.created_at       IS 'Time the row was inserted (not part of the source event).';

CREATE INDEX IF NOT EXISTS idx_call_event_kundennummer ON call_event (kundennummer);
