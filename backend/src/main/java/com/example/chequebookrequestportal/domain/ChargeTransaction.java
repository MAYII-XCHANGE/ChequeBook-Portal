package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cheque_charge_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_charge_request", columnNames = "request_id"),
        @UniqueConstraint(name = "uk_charge_key", columnNames = "idempotency_key") })
public class ChargeTransaction extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ChequeRequest request;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "charge_amount", precision = 18, scale = 2)
    private BigDecimal chargeAmount;
    @Column(name = "vat_amount", precision = 18, scale = 2)
    private BigDecimal vatAmount;
    @Column(name = "vat_rate", precision = 12, scale = 8)
    private BigDecimal vatRate;
    @Column(name = "charge_gl_account", length = 30)
    private String chargeGlAccount;
    @Column(name = "vat_gl_account", length = 30)
    private String vatGlAccount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebitStatus status;
    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;
    @Column(length = 100)
    private String finacleReference;
    @Column(length = 50)
    private String responseCode;
    @Column(length = 1000)
    private String failureReason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private StaffUser initiatedBy;
    private Instant completedAt;
    private int retryCount;
    @Version
    private long version;

    protected ChargeTransaction() {
    }

    public ChargeTransaction(ChequeRequest request, BigDecimal chargeAmount, BigDecimal vatAmount,
            BigDecimal vatRate, String chargeGlAccount, String vatGlAccount, String currency, String key,
            StaffUser actor) {
        this.request = request;
        this.chargeAmount = chargeAmount;
        this.vatAmount = vatAmount;
        this.vatRate = vatRate;
        this.chargeGlAccount = chargeGlAccount;
        this.vatGlAccount = vatGlAccount;
        this.amount = chargeAmount.add(vatAmount);
        this.currency = currency;
        this.idempotencyKey = key;
        this.initiatedBy = actor;
        this.status = DebitStatus.PENDING;
    }

    public void completeDebit(DebitStatus status, String ref, String code, String reason) {
        this.status = status;
        this.finacleReference = ref;
        this.responseCode = code;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public void retryDebit() {
        retryCount++;
        status = DebitStatus.PENDING;
        failureReason = null;
        completedAt = null;
    }

    public void beginReversal() {
        status = DebitStatus.REVERSAL_PENDING;
        failureReason = null;
    }

    public void completeReversal(DebitStatus status, String reason) {
        this.status = status;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public void reconcileUnknownReversal(DebitStatus outcome, String reason) {
        if (status != DebitStatus.REVERSAL_UNKNOWN)
            throw new IllegalStateException("Only an unknown reversal can be reconciled.");
        if (outcome != DebitStatus.REVERSED && outcome != DebitStatus.REVERSAL_FAILED)
            throw new IllegalArgumentException("Unsupported reversal reconciliation outcome.");
        completeReversal(outcome, outcome == DebitStatus.REVERSED ? null : reason);
    }

    public DebitStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getChargeAmount() {
        return chargeAmount == null ? amount : chargeAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public String getChargeGlAccount() {
        return chargeGlAccount;
    }

    public String getVatGlAccount() {
        return vatGlAccount;
    }

    public boolean hasVatBreakdown() {
        return chargeAmount != null && vatAmount != null;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFinacleReference() {
        return finacleReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

}
