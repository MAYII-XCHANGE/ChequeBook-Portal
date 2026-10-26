-- Run with the schema-migration owner before deploying administration email notifications.
-- Hibernate may create an enum CHECK constraint for string-backed enum columns; replace it so
-- the outbox accepts the two administration notification event types.

BEGIN
    FOR event_constraint IN (
        SELECT constraint_name
          FROM user_constraints
         WHERE table_name = 'CHEQUE_EMAIL_OUTBOX'
           AND constraint_type = 'C'
           AND UPPER(search_condition_vc) LIKE '%EVENT_TYPE%'
           AND UPPER(search_condition_vc) LIKE '% IN %'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE cheque_email_outbox DROP CONSTRAINT '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(event_constraint.constraint_name);
    END LOOP;
END;
/

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
