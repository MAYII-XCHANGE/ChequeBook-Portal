package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cheque_vendor_export_items")
public class VendorExportItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private VendorExportBatch batch;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private ChequeRequest request;
    @Column(nullable = false, length = 30)
    private String accountNumber;
    @Column(nullable = false, length = 200)
    private String accountName;
    @Column(nullable = false, length = 150)
    private String branchName;
    @Column(length = 500)
    private String branchAddress;
    @Column(length = 30)
    private String sortCode;
    @Column(nullable = false, length = 30)
    private String chequeTypeCode;
    private int leafCount;
    private int bookletCount;
    @Column(nullable = false, length = 20)
    private String deliverySolId;
    @Column(nullable = false, length = 500)
    private String deliveryAddress;
    @Column(nullable = false, length = 50)
    private String schemeCode;
    @Column(nullable = false, length = 3)
    private String currency;

    protected VendorExportItem() {
    }

    public VendorExportItem(VendorExportBatch batch, ChequeRequest r, String chequeType, String deliverySol,
            String deliveryAddress, String schemeCode) {
        this.batch = batch;
        this.request = r;
        this.accountNumber = r.getAccountNumber();
        this.accountName = r.getAccountName();
        this.branchName = r.getBranchName();
        this.branchAddress = r.getBranchAddress();
        this.sortCode = r.getSortCode();
        this.chequeTypeCode = chequeType;
        this.leafCount = r.getLeafCount();
        this.bookletCount = r.getBookletCount();
        this.deliverySolId = deliverySol;
        this.deliveryAddress = deliveryAddress;
        this.schemeCode = schemeCode;
        this.currency = r.getCurrency();
    }

    public String[] row(int serialNumber) {
        return new String[] { String.valueOf(serialNumber), accountNumber, accountName, branchName, branchAddress,
                sortCode, chequeTypeCode, String.valueOf(leafCount), String.valueOf(bookletCount), deliverySolId,
                deliveryAddress, schemeCode, currency, "", "" };
    }

    public ChequeRequest getRequest() {
        return request;
    }

    public VendorExportBatch getBatch() { return batch; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }
    public String getBranchName() { return branchName; }
    public String getBranchAddress() { return branchAddress; }
    public String getSortCode() { return sortCode; }
    public String getChequeTypeCode() { return chequeTypeCode; }
    public int getLeafCount() { return leafCount; }
    public int getBookletCount() { return bookletCount; }
    public String getDeliverySolId() { return deliverySolId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getSchemeCode() { return schemeCode; }
    public String getCurrency() { return currency; }
}
