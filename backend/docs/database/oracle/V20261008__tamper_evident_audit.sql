-- Run with the schema-migration owner, never the runtime application account.
-- Replace CHEQUEBOOK_PORTAL_RUNTIME with the actual runtime database user or role.

ALTER TABLE audit_events ADD (
    chain_sequence NUMBER(19),
    occurred_at TIMESTAMP WITH TIME ZONE,
    actor_id NUMBER(19),
    actor_username VARCHAR2(200 CHAR),
    previous_hash VARCHAR2(64 CHAR),
    event_hash VARCHAR2(64 CHAR),
    integrity_key_id VARCHAR2(50 CHAR)
);

CREATE UNIQUE INDEX uk_audit_events_chain_sequence
    ON audit_events (chain_sequence);

CREATE TABLE audit_chain_state (
    id NUMBER(19) PRIMARY KEY,
    last_sequence NUMBER(19) NOT NULL,
    last_hash VARCHAR2(64 CHAR) NOT NULL,
    version NUMBER(19) NOT NULL
);

INSERT INTO audit_chain_state (id, last_sequence, last_hash, version)
VALUES (1, 0, RPAD('0', 64, '0'), 0);

REVOKE UPDATE, DELETE ON audit_events FROM CHEQUEBOOK_PORTAL_RUNTIME;
GRANT SELECT, INSERT ON audit_events TO CHEQUEBOOK_PORTAL_RUNTIME;
REVOKE INSERT, DELETE ON audit_chain_state FROM CHEQUEBOOK_PORTAL_RUNTIME;
GRANT SELECT, UPDATE ON audit_chain_state TO CHEQUEBOOK_PORTAL_RUNTIME;

COMMIT;
