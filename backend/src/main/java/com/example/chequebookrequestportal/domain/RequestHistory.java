package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cheque_request_history")
public class RequestHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ChequeRequest request;
    @Column(length = 40)
    private String fromStatus;
    @Column(nullable = false, length = 40)
    private String toStatus;
    @Column(nullable = false, length = 200)
    private String actor;
    @Column(length = 1000)
    private String note;

    protected RequestHistory() {
    }

    public RequestHistory(ChequeRequest request, String from, String to, String actor, String note) {
        this.request = request;
        this.fromStatus = from;
        this.toStatus = to;
        this.actor = actor;
        this.note = note;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getNote() {
        return note;
    }
}
