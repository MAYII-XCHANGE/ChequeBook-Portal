-- Run with the schema-migration owner before deploying administration email notifications.
-- Hibernate may create an enum CHECK constraint for string-backed enum columns; replace it so
-- the outbox accepts the two administration notification event types.

BEGIN;

DO $$
DECLARE
    event_constraint RECORD;
BEGIN
    FOR event_constraint IN
        SELECT constraint_name
          FROM information_schema.check_constraints
         WHERE constraint_schema = current_schema()
           AND constraint_name IN (
               SELECT constraint_name
                 FROM information_schema.constraint_column_usage
                WHERE table_schema = current_schema()
                  AND table_name = 'cheque_email_outbox'
                  AND column_name = 'event_type'
           )
    LOOP
        EXECUTE format('ALTER TABLE cheque_email_outbox DROP CONSTRAINT %I',
                event_constraint.constraint_name);
    END LOOP;
END $$;

ALTER TABLE cheque_email_outbox
    ADD CONSTRAINT ck_cheque_email_outbox_event_type CHECK (event_type IN (
        'APPROVAL_REQUIRED',
        'OPERATIONS_EXPORT_READY',
        'OPERATIONS_DEBIT_REVIEW_REQUIRED',
        'OPERATIONS_REVERSAL_REVIEW_REQUIRED',
        'REQUEST_CANCELLED_AFTER_REVERSAL',
        'BRANCH_DISPATCHED',
        'BRANCH_RECEIVED',
        'CUSTOMER_COLLECTION_CONFIRMED',
        'ADMIN_BRANCH_MODIFIED',
        'ADMIN_USER_MODIFIED'
    ));

COMMIT;
