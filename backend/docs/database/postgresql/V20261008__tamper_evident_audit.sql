-- Run with the schema-migration owner, never the runtime application account.
-- Replace chequebook_portal_runtime with the actual runtime database role.

BEGIN;

ALTER TABLE audit_events ADD COLUMN chain_sequence BIGINT;
ALTER TABLE audit_events ADD COLUMN occurred_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE audit_events ADD COLUMN actor_id BIGINT;
ALTER TABLE audit_events ADD COLUMN actor_username VARCHAR(200);
ALTER TABLE audit_events ADD COLUMN previous_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN event_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN integrity_key_id VARCHAR(50);

CREATE UNIQUE INDEX uk_audit_events_chain_sequence
    ON audit_events (chain_sequence);

CREATE TABLE audit_chain_state (
    id BIGINT PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    last_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL
);

INSERT INTO audit_chain_state (id, last_sequence, last_hash, version)
VALUES (1, 0, repeat('0', 64), 0);

REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM chequebook_portal_runtime;
GRANT SELECT, INSERT ON audit_events TO chequebook_portal_runtime;
REVOKE INSERT, DELETE, TRUNCATE ON audit_chain_state FROM chequebook_portal_runtime;
GRANT SELECT, UPDATE ON audit_chain_state TO chequebook_portal_runtime;

COMMIT;
