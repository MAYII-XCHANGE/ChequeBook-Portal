CREATE TABLE cheque_type_sequence_locks (
    cheque_type_code VARCHAR2(2 CHAR) PRIMARY KEY,
    CONSTRAINT ck_cheque_type_sequence_lock CHECK (cheque_type_code IN ('01', '02'))
);

INSERT INTO cheque_type_sequence_locks (cheque_type_code) VALUES ('01');
INSERT INTO cheque_type_sequence_locks (cheque_type_code) VALUES ('02');

CREATE TABLE cheque_serial_allocations (
    id NUMBER(19) PRIMARY KEY,
    request_id NUMBER(19) NOT NULL,
    batch_id NUMBER(19) NOT NULL,
    cheque_type_code VARCHAR2(2 CHAR) NOT NULL,
    start_cheque_number VARCHAR2(30 CHAR) NOT NULL,
    end_cheque_number VARCHAR2(30 CHAR) NOT NULL,
    leaf_count NUMBER(10) NOT NULL,
    vendor_reference VARCHAR2(100 CHAR) NOT NULL,
    print_status VARCHAR2(20 CHAR) NOT NULL,
    imported_by NUMBER(19) NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finacle_registration_status VARCHAR2(40 CHAR) NOT NULL,
    finacle_host_reference VARCHAR2(100 CHAR),
    finacle_response_code VARCHAR2(50 CHAR),
    failure_reason VARCHAR2(1000 CHAR),
    registered_at TIMESTAMP WITH TIME ZONE,
    version NUMBER(19) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cheque_serial_request UNIQUE (request_id),
    CONSTRAINT fk_cheque_serial_request FOREIGN KEY (request_id) REFERENCES cheque_requests(id),
    CONSTRAINT fk_cheque_serial_batch FOREIGN KEY (batch_id) REFERENCES cheque_vendor_export_batches(id),
    CONSTRAINT fk_cheque_serial_importer FOREIGN KEY (imported_by) REFERENCES cheque_staff_users(id),
    CONSTRAINT ck_cheque_serial_type CHECK (cheque_type_code IN ('01', '02')),
    CONSTRAINT ck_cheque_serial_print_status CHECK (print_status = 'PRINTED'),
    CONSTRAINT ck_cheque_serial_leaf_count CHECK (leaf_count IN (50, 100))
);

CREATE INDEX ix_cheque_serial_type_range
    ON cheque_serial_allocations (cheque_type_code, start_cheque_number, end_cheque_number);

COMMIT;
