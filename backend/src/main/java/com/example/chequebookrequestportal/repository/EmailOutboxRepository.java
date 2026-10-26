package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.email.EmailDeliveryStatus;
import com.example.chequebookrequestportal.email.EmailOutboxMessage;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, Long> {
    boolean existsByDeduplicationKey(String deduplicationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select message from EmailOutboxMessage message
            where message.status in :statuses
              and message.nextAttemptAt <= :now
              and message.attemptCount < :maximumAttempts
            order by message.createdAt asc
            """)
    List<EmailOutboxMessage> findDispatchable(@Param("statuses") Collection<EmailDeliveryStatus> statuses,
            @Param("now") Instant now, @Param("maximumAttempts") int maximumAttempts, Pageable pageable);
}
