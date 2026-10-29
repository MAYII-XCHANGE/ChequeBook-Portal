package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.time.Instant;

@Entity
@Table(name = "cheque_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_request_number", columnNames = "request_number"),
        @UniqueConstraint(name = "uk_active_account_request", columnNames = { "account_number", "active_marker" })
})
public class ChequeRequest extends BaseEntity {
    @Column(name = "request_number", unique = true, length = 30)
    private String requestNumber;
    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;
    @Column(nullable = false, length = 200)
    private String accountName;
    @Column(length = 100)
    private String acctOwnership;
    @Column(length = 50)
    private String schemeCode;
    @Convert(converter = ChequeTypeConverter.class)
    @Column(name = "cheque_type_code", length = 2)
    private ChequeType chequeType;
    @Enumerated(EnumType.STRING)
    @Column(name = "debit_policy", length = 30)
    private DebitPolicy debitPolicy = DebitPolicy.CUSTOMER_DEBIT;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "account_home_branch_code", length = 20)
    private String accountHomeBranchCode;
    @Column(name = "account_home_branch_name", length = 150)
    private String accountHomeBranchName;
    @Column(nullable = false, length = 20)
    private String branchCode;
    @Column(nullable = false, length = 150)
    private String branchName;
    @Column(length = 500)
    private String branchAddress;
    @Column(length = 30)
    private String sortCode;
    @Column(nullable = false)
    private int leafCount;
    @Column(nullable = false)
    private int bookletCount = 1;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RequestStatus status;
    @Column(name = "active_marker", precision = 19)
    private Long activeMarker = 1L;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private StaffUser createdBy;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private StaffUser approvedBy;
    @OneToOne(mappedBy = "request", fetch = FetchType.EAGER)
    private ChequeSerialAllocation serialAllocation;
    private Instant approvedAt;
    @Column(length = 1000)
    private String rejectionReason;
    @Version
    private long version;

    protected ChequeRequest() {
    }

    public ChequeRequest(String accountNumber, String accountName, String acctOwnership, String schemeCode,
            String currency,
            String branchCode, String branchName, String branchAddress, String sortCode, int leafCount,
            StaffUser createdBy) {
        this(accountNumber, accountName, acctOwnership, schemeCode, null, currency, null, null, branchCode,
                branchName, branchAddress, sortCode, leafCount, createdBy);
    }

    public ChequeRequest(String accountNumber, String accountName, String acctOwnership, String schemeCode,
            String currency, String accountHomeBranchCode, String accountHomeBranchName,
            String branchCode, String branchName, String branchAddress, String sortCode, int leafCount,
            StaffUser createdBy) {
        this(accountNumber, accountName, acctOwnership, schemeCode, null, currency, accountHomeBranchCode,
                accountHomeBranchName, branchCode, branchName, branchAddress, sortCode, leafCount, createdBy);
    }

    public ChequeRequest(String accountNumber, String accountName, String acctOwnership, String schemeCode,
            ChequeType chequeType, String currency, String accountHomeBranchCode, String accountHomeBranchName,
            String branchCode, String branchName, String branchAddress, String sortCode, int leafCount,
            StaffUser createdBy) {
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.acctOwnership = acctOwnership;
        this.schemeCode = schemeCode;
        this.chequeType = chequeType;
        this.debitPolicy = chequeType != null && !chequeType.requiresCustomerDebit()
                ? DebitPolicy.NOT_REQUIRED : DebitPolicy.CUSTOMER_DEBIT;
        this.currency = currency;
        this.accountHomeBranchCode = accountHomeBranchCode;
        this.accountHomeBranchName = accountHomeBranchName;
        this.branchCode = branchCode;
        this.branchName = branchName;
        this.branchAddress = branchAddress;
        this.sortCode = sortCode;
        this.leafCount = leafCount;
        this.createdBy = createdBy;
        this.status = RequestStatus.PENDING_APPROVAL;
    }

    public void assignRequestNumber(String value) {
        if (requestNumber == null)
            requestNumber = value;
    }

    public void setStatus(RequestStatus status) {
        if (isTerminal(status)) {
            if (getId() == null)
                throw new IllegalStateException("A request must be persisted before becoming terminal.");
            activeMarker = Math.negateExact(getId());
        }
        else {
            activeMarker = 1L;
        }
        this.status = status;
    }

    public void approve(StaffUser approver) {
        approvedBy = approver;
        approvedAt = Instant.now();
    }

    public void reject(StaffUser approver, String reason) {
        approvedBy = approver;
        approvedAt = Instant.now();
        rejectionReason = reason;
        setStatus(RequestStatus.REJECTED);
    }

    private boolean isTerminal(RequestStatus s) {
        return s == RequestStatus.REJECTED || s == RequestStatus.DELIVERED || s == RequestStatus.CANCELLED;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getacctOwnership() {
        return acctOwnership;
    }

    public String getSchemeCode() {
        return schemeCode;
    }

    public ChequeType getChequeType() {
        return chequeType;
    }

    public DebitPolicy getDebitPolicy() {
        return debitPolicy == null ? DebitPolicy.CUSTOMER_DEBIT : debitPolicy;
    }

    public boolean requiresCustomerDebit() {
        return getDebitPolicy() == DebitPolicy.CUSTOMER_DEBIT;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAccountHomeBranchCode() {
        return accountHomeBranchCode;
    }

    public String getAccountHomeBranchName() {
        return accountHomeBranchName;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getBranchAddress() {
        return branchAddress;
    }

    public String getSortCode() {
        return sortCode;
    }

    public int getLeafCount() {
        return leafCount;
    }

    public int getBookletCount() {
        return bookletCount;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public StaffUser getCreatedBy() {
        return createdBy;
    }

    public StaffUser getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public ChequeSerialAllocation getSerialAllocation() {
        return serialAllocation;
    }

    public void attachSerialAllocation(ChequeSerialAllocation allocation) {
        if (serialAllocation != null && serialAllocation != allocation)
            throw new IllegalStateException("This request already has a cheque serial allocation.");
        serialAllocation = allocation;
    }

    public long getVersion() {
        return version;
    }
}
