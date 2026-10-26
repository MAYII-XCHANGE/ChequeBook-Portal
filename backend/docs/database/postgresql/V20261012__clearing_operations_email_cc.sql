-- Run with the schema-migration owner before deploying the application update.

BEGIN;

ALTER TABLE email_outbox ADD COLUMN cc_address VARCHAR(320);

COMMIT;
