-- Existing requests remain nullable so their cheque type can be reviewed and backfilled safely.
ALTER TABLE cheque_requests
    ADD (cheque_type_code VARCHAR2(2 CHAR));

ALTER TABLE cheque_requests
    ADD CONSTRAINT ck_cheque_requests_type
    CHECK (cheque_type_code IS NULL OR cheque_type_code IN ('01', '02'));

-- Before exporting a legacy request, backfill its confirmed business type, for example:
-- UPDATE cheque_requests SET cheque_type_code = '01' WHERE request_number = 'CBR-...';
