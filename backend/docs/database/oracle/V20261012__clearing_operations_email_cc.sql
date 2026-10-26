-- Run with the schema-migration owner before deploying the application update.

ALTER TABLE email_outbox ADD (
    cc_address VARCHAR2(320 CHAR)
);

COMMIT;
