package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "cheque_number_counter")
public class ChequeNumber {
    public static final long COUNTER_ID = 1L;

    @Id
    private Long id = COUNTER_ID;

    @Column(name = "check_number", nullable = false)
    private long checkNumber = 0;

    @Column(name = "reconciliation_required", nullable = false)
    private boolean reconciliationRequired;

    @Column(name = "reconciliation_request_number", length = 30)
    private String reconciliationRequestNumber;

    @Version
    private long version;

    protected ChequeNumber() {
    }

    public static ChequeNumber startingAtZero() {
        return new ChequeNumber();
    }

    public long getCheckNumber() {
        return checkNumber;
    }

    public boolean isReconciliationRequired() {
        return reconciliationRequired;
    }

    public String getReconciliationRequestNumber() {
        return reconciliationRequestNumber;
    }

    public void advanceBy(int leafCount) {
        if (leafCount <= 0)
            throw new IllegalArgumentException("leafCount must be greater than zero");
        checkNumber = Math.addExact(checkNumber, leafCount);
    }

    public void requireReconciliation(String requestNumber) {
        reconciliationRequired = true;
        reconciliationRequestNumber = requestNumber;
    }
}
