package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cheque_eligibility_results")
public class EligibilityRecord extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private ChequeRequest request;
    @Column(nullable = false)
    private boolean eligible;
    @Column(length = 30)
    private String accountStatus;
    @Column(length = 30)
    private String kycLevel;
    @Column(length = 500)
    private String restrictions;
    @Column(length = 1000)
    private String reason;
    @Column(length = 100)
    private String finacleReference;

    protected EligibilityRecord() {
    }

    public EligibilityRecord(ChequeRequest request, boolean eligible, String accountStatus, String kycLevel,
            String restrictions, String reason, String reference) {
        this.request = request;
        this.eligible = eligible;
        this.accountStatus = accountStatus;
        this.kycLevel = kycLevel;
        this.restrictions = restrictions;
        this.reason = reason;
        this.finacleReference = reference;
    }
}
