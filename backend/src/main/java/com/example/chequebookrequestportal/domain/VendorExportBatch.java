package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "cheque_vendor_export_batches")
public class VendorExportBatch extends BaseEntity {
    public static final String STATUS_EXPORTED = "EXPORTED";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_PRINTED = "PRINTED";

    @Column(unique = true, length = 40)
    private String batchNumber;
    @Column(nullable = false, length = 255)
    private String fileName;
    @Column(length = 128)
    private String fileChecksum;
    @Column(nullable = false)
    private int recordCount;
    @Column(nullable = false, length = 30)
    private String status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by", nullable = false)
    private StaffUser generatedBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private StaffUser sentBy;
    private Instant sentAt;
    @Version
    private long version;

    protected VendorExportBatch() {
    }

    public VendorExportBatch(int count, StaffUser actor, String provisionalFileName) {
        if (provisionalFileName == null || provisionalFileName.isBlank())
            throw new IllegalArgumentException("A provisional export filename is required.");
        this.recordCount = count;
        this.generatedBy = actor;
        this.fileName = provisionalFileName;
        this.status = STATUS_EXPORTED;
    }

    public void assignIdentity(String number) {
        if (batchNumber != null)
            throw new IllegalStateException("Vendor export identity has already been assigned.");
        if (number == null || number.isBlank())
            throw new IllegalArgumentException("Vendor export identity is required.");
        this.batchNumber = number;
        this.fileName = number + ".xlsx";
    }

    public void setChecksum(String value) {
        this.fileChecksum = value;
    }

    public void confirmSent(StaffUser actor) {
        status = STATUS_SENT;
        sentBy = actor;
        sentAt = Instant.now();
    }

    public void confirmPrinted() {
        if (STATUS_PRINTED.equals(status))
            return;
        if (!STATUS_SENT.equals(status))
            throw new IllegalStateException("Only a sent vendor batch can be marked as printed.");
        status = STATUS_PRINTED;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
