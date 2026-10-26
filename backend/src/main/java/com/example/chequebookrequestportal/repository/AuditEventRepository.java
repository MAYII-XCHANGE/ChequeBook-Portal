package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findByActorContainingIgnoreCaseOrActionContainingIgnoreCaseOrEntityIdContainingIgnoreCase(
            String actor, String action, String entityId, Pageable pageable);
}
