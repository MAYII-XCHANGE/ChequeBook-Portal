package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cheque_audit_events")
public class AuditEvent extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String actor;
    @Column(nullable = false, length = 100)
    private String actorRole;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(nullable = false, length = 50)
    private String entityType;
    @Column(nullable = false, length = 100)
    private String entityId;
    @Column(length = 1000)
    private String description;
    @Column(length = 100)
    private String ipAddress;
    @Column(length = 100)
    private String correlationId;

    protected AuditEvent() {
    }

    public AuditEvent(String actor, String role, String action, String type, String entityId, String description,
            String ip, String correlationId) {
        this.actor = actor;
        this.actorRole = role;
        this.action = action;
        this.entityType = type;
        this.entityId = entityId;
        this.description = description;
        this.ipAddress = ip;
        this.correlationId = correlationId;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDescription() {
        return description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
