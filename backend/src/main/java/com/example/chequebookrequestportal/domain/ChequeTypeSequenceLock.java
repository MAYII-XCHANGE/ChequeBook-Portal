package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cheque_type_sequence_locks")
public class ChequeTypeSequenceLock {
    @Id
    @Column(name = "cheque_type_code", length = 2)
    private String chequeTypeCode;

    protected ChequeTypeSequenceLock() {
    }

    public ChequeTypeSequenceLock(String chequeTypeCode) {
        this.chequeTypeCode = chequeTypeCode;
    }

    public String getChequeTypeCode() {
        return chequeTypeCode;
    }
}
