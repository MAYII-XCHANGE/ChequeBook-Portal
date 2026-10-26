package com.example.chequebookrequestportal.email;

import com.example.chequebookrequestportal.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "cheque_email_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_outbox_dedup", columnNames = "deduplication_key")
}, indexes = {
        @Index(name = "ix_email_outbox_dispatches", columnList = "delivery_status,next_attempt_at")
})
public class EmailOutboxMessage extends BaseEntity {
    @Column(name = "deduplication_key", nullable = false, length = 64)
    private String deduplicationKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private EmailEventType eventType;
    @Column(name = "request_number", nullable = false, length = 30)
    private String requestNumber;
    @Column(name = "recipient_address", nullable = false, length = 320)
    private String recipientAddress;
    @Column(name = "recipient_name", length = 200)
    private String recipientName;
    @Column(name = "cc_address", length = 320)
    private String ccAddress;
    @Column(nullable = false, length = 255)
    private String subject;
    @Lob
    @Column(nullable = false)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Version
    private long version;

    protected EmailOutboxMessage() {
    }

    public EmailOutboxMessage(String deduplicationKey, EmailEventType eventType, String requestNumber,
            String recipientAddress, String recipientName, String ccAddress, String subject, String body) {
        this.deduplicationKey = deduplicationKey;
        this.eventType = eventType;
        this.requestNumber = requestNumber;
        this.recipientAddress = recipientAddress;
        this.recipientName = recipientName;
        this.ccAddress = ccAddress == null || ccAddress.isBlank() ? null : ccAddress.trim();
        this.subject = subject;
        this.body = body;
        this.nextAttemptAt = Instant.now();
    }

    public void markSent() {
        attemptCount++;
        status = EmailDeliveryStatus.SENT;
        sentAt = Instant.now();
        lastError = null;
    }

    public void markFailure(String error, int maximumAttempts, Instant nextAttempt) {
        attemptCount++;
        lastError = truncate(error, 1000);
        if (attemptCount >= maximumAttempts) {
            status = EmailDeliveryStatus.FAILED;
        }
        else {
            status = EmailDeliveryStatus.RETRYING;
            nextAttemptAt = nextAttempt;
        }
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.isBlank())
            return "Mail delivery failed.";
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public EmailEventType getEventType() {
        return eventType;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getCcAddress() {
        return ccAddress;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public EmailDeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }
}
