package com.example.chequebookrequestportal.email;

import com.example.chequebookrequestportal.repository.EmailOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class EmailOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private final EmailOutboxRepository outbox;
    private final EmailGatewayClient emailGateway;
    private final int maximumAttempts;
    private final int batchSize;

    public EmailOutboxDispatcher(EmailOutboxRepository outbox, EmailGatewayClient emailGateway,
            @Value("${app.email.max-attempts:5}") int maximumAttempts,
            @Value("${app.email.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.emailGateway = emailGateway;
        this.maximumAttempts = Math.max(1, maximumAttempts);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${app.email.poll-interval:30s}")
    @Transactional
    public void dispatch() {
        var messages = outbox.findDispatchable(
                EnumSet.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.RETRYING), Instant.now(), maximumAttempts,
                PageRequest.of(0, batchSize));
        for (EmailOutboxMessage message : messages)
            deliver(message);
    }

    private void deliver(EmailOutboxMessage message) {
        try {
            emailGateway.send(message);
            message.markSent();
            log.info("Sent workflow email {} for request {}", message.getEventType(), message.getRequestNumber());
        }
        catch (RuntimeException exception) {
            long delayMinutes = Math.min(60, 1L << Math.min(message.getAttemptCount(), 6));
            message.markFailure(exception.getMessage(), maximumAttempts,
                    Instant.now().plus(Duration.ofMinutes(delayMinutes)));
            log.warn("Workflow email {} for request {} could not be delivered; attempt {} of {}",
                    message.getEventType(), message.getRequestNumber(), message.getAttemptCount(), maximumAttempts);
        }
    }
}
