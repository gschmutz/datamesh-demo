-- 1) Target database (remove this line to use the default POSTGRES_DB).
\connect demodb

-- 2) Target schema: create it and make it the default for this script.
CREATE SCHEMA IF NOT EXISTS ticket;
SET search_path TO ticket;

-- -------------------------------------------------------
-- User (renamed to avoid collision with pg reserved word)
-- -------------------------------------------------------

CREATE TABLE agent_user (
    id                          BIGINT PRIMARY KEY,
    login                       VARCHAR(255) NOT NULL UNIQUE,
    email                       VARCHAR(255) NOT NULL,
    firstname                   VARCHAR(128),
    lastname                    VARCHAR(128),
    image                       VARCHAR(64),        -- md5 hash ref
    image_source                TEXT,
    source                      VARCHAR(128),
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    verified                    BOOLEAN NOT NULL DEFAULT FALSE,
    out_of_office               BOOLEAN NOT NULL DEFAULT FALSE,
    out_of_office_start_at      TIMESTAMP WITH TIME ZONE,
    out_of_office_end_at        TIMESTAMP WITH TIME ZONE,
    out_of_office_replacement_id BIGINT REFERENCES agent_user(id),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_id               BIGINT REFERENCES agent_user(id),
    updated_by_id               BIGINT REFERENCES agent_user(id)
);

-- -------------------------------------------------------
-- Ticket group
-- -------------------------------------------------------

CREATE TABLE ticket_group (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(255) NOT NULL UNIQUE,
    name_last           VARCHAR(255),
    note                TEXT,
    email_address_id    BIGINT,                     -- FK to email_address table (out of scope)
    parent_id           BIGINT REFERENCES ticket_group(id),
    assignment_timeout  INTEGER,                    -- minutes
    reopen_time_in_days INTEGER,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    follow_up_assignment BOOLEAN NOT NULL DEFAULT TRUE,
    follow_up_possible  VARCHAR(32) NOT NULL DEFAULT 'yes'
                            CHECK (follow_up_possible IN ('yes', 'no', 'new_ticket')),
    shared_drafts       BOOLEAN NOT NULL DEFAULT TRUE,
    signature           VARCHAR(128),
    signature_id        BIGINT,
    summary_generation  VARCHAR(32) NOT NULL DEFAULT 'global_default'
                            CHECK (summary_generation IN ('global_default', 'enabled', 'disabled')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_id       BIGINT REFERENCES agent_user(id),
    updated_by_id       BIGINT REFERENCES agent_user(id)
);

-- -------------------------------------------------------
-- Ticket
-- -------------------------------------------------------

CREATE TABLE ticket (
    id                              BIGINT PRIMARY KEY,
    number                          VARCHAR(32) NOT NULL UNIQUE,
    title                           VARCHAR(255) NOT NULL,
    state                           VARCHAR(64) NOT NULL,
    group_id                        BIGINT NOT NULL REFERENCES ticket_group(id),
    priority_name                   VARCHAR(64) NOT NULL,
    priority_note                   TEXT,
    owner_id                        BIGINT NOT NULL REFERENCES agent_user(id),
    customer_id                     BIGINT,
    create_article_type             VARCHAR(64) NOT NULL,
    create_article_sender           VARCHAR(64) NOT NULL,
    ai_agent_running                BOOLEAN NOT NULL DEFAULT FALSE,
    checklist_id                    BIGINT,
    note                            TEXT,
    pending_time                    TIMESTAMP WITH TIME ZONE,
    -- Escalation / SLA timestamps
    escalation_at                   TIMESTAMP WITH TIME ZONE,
    close_escalation_at             TIMESTAMP WITH TIME ZONE,
    first_response_escalation_at    TIMESTAMP WITH TIME ZONE,
    update_escalation_at            TIMESTAMP WITH TIME ZONE,
    -- Contact timestamps
    last_contact_at                 TIMESTAMP WITH TIME ZONE,
    last_contact_agent_at           TIMESTAMP WITH TIME ZONE,
    last_contact_customer_at        TIMESTAMP WITH TIME ZONE,
    last_owner_update_at            TIMESTAMP WITH TIME ZONE,
    -- Resolution timestamps
    first_response_at               TIMESTAMP WITH TIME ZONE,
    close_at                        TIMESTAMP WITH TIME ZONE,
    last_close_at                   TIMESTAMP WITH TIME ZONE,
    -- SLA durations (minutes)
    first_response_in_min           INTEGER,
    first_response_diff_in_min      INTEGER,
    close_in_min                    INTEGER,
    close_diff_in_min               INTEGER,
    update_in_min                   INTEGER,
    update_diff_in_min              INTEGER,
    -- Time accounting
    time_unit                       NUMERIC(10,2),
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_id                   BIGINT REFERENCES agent_user(id),
    updated_by_id                   BIGINT REFERENCES agent_user(id)
);

-- -------------------------------------------------------
-- Article
-- -------------------------------------------------------

CREATE TABLE article (
    id                  BIGINT PRIMARY KEY,
    ticket_id           BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    article_type        VARCHAR(64) NOT NULL,
    article_sender      VARCHAR(64) NOT NULL,
    body                TEXT NOT NULL,
    content_type        VARCHAR(32) NOT NULL DEFAULT 'text/html'
                            CHECK (content_type IN ('text/html', 'text/plain')),
    from_address        VARCHAR(512),
    to_address          VARCHAR(512),
    subject             VARCHAR(512),
    cc                  VARCHAR(512),
    reply_to            VARCHAR(512),
    in_reply_to         VARCHAR(512),
    message_id          VARCHAR(512),
    message_id_md5      VARCHAR(32),
    internal            BOOLEAN NOT NULL DEFAULT FALSE,
    body_rendering_error BOOLEAN NOT NULL DEFAULT FALSE,
    origin_by           VARCHAR(255),
    origin_by_id        BIGINT REFERENCES agent_user(id),
    detected_language   VARCHAR(16),
    time_unit           NUMERIC(10,2),
    accounted_time      NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_id       BIGINT REFERENCES agent_user(id),
    updated_by_id       BIGINT REFERENCES agent_user(id)
);

-- -------------------------------------------------------
-- Indexes
-- -------------------------------------------------------

CREATE INDEX idx_ticket_group        ON ticket(group_id);
CREATE INDEX idx_ticket_customer     ON ticket(customer_id);
CREATE INDEX idx_ticket_owner        ON ticket(owner_id);
CREATE INDEX idx_ticket_priority     ON ticket(priority_id);
CREATE INDEX idx_article_ticket      ON article(ticket_id);
CREATE INDEX idx_article_created_by  ON article(created_by_id);
CREATE INDEX idx_user_email          ON agent_user(email);