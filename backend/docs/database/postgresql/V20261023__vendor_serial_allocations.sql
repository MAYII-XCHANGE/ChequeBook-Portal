CREATE TABLE cheque_type_sequence_locks (
    cheque_type_code VARCHAR(2) PRIMARY KEY,
    CONSTRAINT ck_cheque_type_sequence_lock CHECK (cheque_type_code IN ('01', '02'))
);

INSERT INTO cheque_type_sequence_locks (cheque_type_code) VALUES ('01'), ('02');

CREATE TABLE cheque_serial_allocations (
    id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    cheque_type_code VARCHAR(2) NOT NULL,
    start_cheque_number VARCHAR(30) NOT NULL,
    end_cheque_number VARCHAR(30) NOT NULL,
    leaf_count INTEGER NOT NULL,
    vendor_reference VARCHAR(100) NOT NULL,
    print_status VARCHAR(20) NOT NULL,
    imported_by BIGINT NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finacle_registration_status VARCHAR(40) NOT NULL,
    finacle_host_reference VARCHAR(100),
    finacle_response_code VARCHAR(50),
    failure_reason VARCHAR(1000),
    registered_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
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
