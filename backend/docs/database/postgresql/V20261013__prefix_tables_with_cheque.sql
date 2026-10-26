-- Run once with the schema-migration owner while the application is stopped.
-- This migration renames tables in place; it does not delete or copy application data.

BEGIN;

ALTER TABLE audit_events RENAME TO cheque_audit_events;
ALTER TABLE branches RENAME TO cheque_branches;
ALTER TABLE charge_transactions RENAME TO cheque_charge_transactions;
ALTER TABLE eligibility_results RENAME TO cheque_eligibility_results;
ALTER TABLE roles RENAME TO cheque_roles;
ALTER TABLE role_permissions RENAME TO cheque_role_permissions;
ALTER TABLE request_history RENAME TO cheque_request_history;
ALTER TABLE staff_users RENAME TO cheque_staff_users;
ALTER TABLE staff_roles RENAME TO cheque_staff_roles;
ALTER TABLE vendor_export_batches RENAME TO cheque_vendor_export_batches;
ALTER TABLE vendor_export_items RENAME TO cheque_vendor_export_items;
ALTER TABLE email_outbox RENAME TO cheque_email_outbox;

-- audit_chain_state is created by the optional tamper-evident-audit migration.
ALTER TABLE IF EXISTS audit_chain_state RENAME TO cheque_audit_chain_state;

COMMIT;
