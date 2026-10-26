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
import java.time.Instant;

@Entity
@Table(name = "cheque_serial_allocations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cheque_serial_request", columnNames = "request_id")
})
public class ChequeSerialAllocation extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ChequeRequest request;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private VendorExportBatch batch;
    @Column(name = "cheque_type_code", nullable = false, length = 2)
    private String chequeTypeCode;
    @Column(name = "start_cheque_number", nullable = false, length = 30)
    private String startChequeNumber;
    @Column(name = "end_cheque_number", nullable = false, length = 30)
    private String endChequeNumber;
    @Column(nullable = false)
    private int leafCount;
    @Column(name = "vendor_reference", nullable = false, length = 100)
    private String vendorReference;
    @Column(name = "print_status", nullable = false, length = 20)
    private String printStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by", nullable = false)
    private StaffUser importedBy;
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "finacle_registration_status", nullable = false, length = 40)
    private FinacleRegistrationStatus finacleRegistrationStatus;
    @Column(name = "finacle_host_reference", length = 100)
    private String finacleHostReference;
    @Column(name = "finacle_response_code", length = 50)
    private String finacleResponseCode;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "registered_at")
    private Instant registeredAt;
    @Version
    private long version;

    protected ChequeSerialAllocation() {
    }

    public ChequeSerialAllocation(ChequeRequest request, VendorExportBatch batch, String chequeTypeCode,
            String startChequeNumber, String endChequeNumber, int leafCount, String vendorReference,
            StaffUser importedBy) {
        this.request = request;
        this.batch = batch;
        this.chequeTypeCode = chequeTypeCode;
        this.startChequeNumber = startChequeNumber;
        this.endChequeNumber = endChequeNumber;
        this.leafCount = leafCount;
        this.vendorReference = vendorReference;
        this.printStatus = "PRINTED";
        this.importedBy = importedBy;
        this.importedAt = Instant.now();
        this.finacleRegistrationStatus = FinacleRegistrationStatus.NOT_STARTED;
    }

    public boolean matches(String type, String start, String end, int leaves, String reference) {
        return chequeTypeCode.equals(type) && startChequeNumber.equals(start) && endChequeNumber.equals(end)
                && leafCount == leaves && vendorReference.equals(reference);
    }

    public void beginRegistration() {
        if (finacleRegistrationStatus != FinacleRegistrationStatus.NOT_STARTED
                && finacleRegistrationStatus != FinacleRegistrationStatus.FAILED)
            throw new IllegalStateException("This allocation is not available for Finacle registration.");
        finacleRegistrationStatus = FinacleRegistrationStatus.PENDING;
        failureReason = null;
        finacleResponseCode = null;
    }

    public void completeRegistration(String hostReference, String responseCode) {
        finacleRegistrationStatus = FinacleRegistrationStatus.SUCCESSFUL;
        finacleHostReference = hostReference;
        finacleResponseCode = responseCode;
        failureReason = null;
        registeredAt = Instant.now();
    }

    public void failRegistration(String responseCode, String reason) {
        finacleRegistrationStatus = FinacleRegistrationStatus.FAILED;
        finacleResponseCode = responseCode;
        failureReason = reason;
    }

    public void requireReview(String responseCode, String reason) {
        finacleRegistrationStatus = FinacleRegistrationStatus.REVIEW_REQUIRED;
        finacleResponseCode = responseCode;
        failureReason = reason;
    }

    public void reconcileAsRegistered() {
        completeRegistration(finacleHostReference, "MANUALLY_CONFIRMED");
    }

    public void reconcileAsNotRegistered(String evidence) {
        failRegistration("MANUALLY_CONFIRMED_NOT_REGISTERED", evidence);
    }

    public ChequeRequest getRequest() { return request; }
    public VendorExportBatch getBatch() { return batch; }
    public String getChequeTypeCode() { return chequeTypeCode; }
    public String getStartChequeNumber() { return startChequeNumber; }
    public String getEndChequeNumber() { return endChequeNumber; }
    public int getLeafCount() { return leafCount; }
    public String getVendorReference() { return vendorReference; }
    public String getPrintStatus() { return printStatus; }
    public StaffUser getImportedBy() { return importedBy; }
    public Instant getImportedAt() { return importedAt; }
    public FinacleRegistrationStatus getFinacleRegistrationStatus() { return finacleRegistrationStatus; }
    public String getFinacleHostReference() { return finacleHostReference; }
    public String getFinacleResponseCode() { return finacleResponseCode; }
    public String getFailureReason() { return failureReason; }
    public Instant getRegisteredAt() { return registeredAt; }
}
