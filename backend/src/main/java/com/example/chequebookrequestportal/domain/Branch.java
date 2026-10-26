package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "cheque_branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_branch_charge_gl", columnNames = "charge_gl_account"),
        @UniqueConstraint(name = "uk_branch_vat_gl", columnNames = "vat_gl_account")
})
public class Branch extends BaseEntity {
    @Column(nullable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String address;
    @Column(length = 30)
    private String sortCode;
    @Column(name = "charge_gl_account", length = 30)
    private String chargeGlAccount;
    @Column(name = "vat_gl_account", length = 30)
    private String vatGlAccount;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private boolean systemDefined;

    protected Branch() {
    }

    public Branch(String code, String name, String address, String sortCode, String chargeGlAccount,
            String vatGlAccount) {
        this(code, name, address, sortCode, chargeGlAccount, vatGlAccount, false);
    }

    public Branch(String code, String name, String address, String sortCode, String chargeGlAccount,
            String vatGlAccount, boolean systemDefined) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.sortCode = sortCode;
        this.chargeGlAccount = chargeGlAccount;
        this.vatGlAccount = vatGlAccount;
        this.systemDefined = systemDefined;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getSortCode() {
        return sortCode;
    }

    public String getChargeGlAccount() {
        return chargeGlAccount;
    }

    public String getVatGlAccount() {
        return vatGlAccount;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSystemDefined() {
        return systemDefined;
    }

    public void update(String name, String address, String sortCode, String chargeGlAccount, String vatGlAccount) {
        this.name = name;
        this.address = address;
        this.sortCode = sortCode;
        this.chargeGlAccount = chargeGlAccount;
        this.vatGlAccount = vatGlAccount;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void maintainSystemDefinition(String name, String address, String sortCode, String chargeGlAccount,
            String vatGlAccount) {
        this.name = name;
        this.address = address;
        this.sortCode = sortCode;
        this.chargeGlAccount = chargeGlAccount;
        this.vatGlAccount = vatGlAccount;
        this.active = true;
        this.systemDefined = true;
    }
}
