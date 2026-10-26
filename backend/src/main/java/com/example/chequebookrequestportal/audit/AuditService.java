package com.example.chequebookrequestportal.audit;

import com.example.chequebookrequestportal.domain.AuditEvent;
import com.example.chequebookrequestportal.repository.AuditEventRepository;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import com.example.chequebookrequestportal.security.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final SecurityService security;
    private final HttpServletRequest request;

    public AuditService(AuditEventRepository events, SecurityService security, HttpServletRequest request) {
        this.events = events;
        this.security = security;
        this.request = request;
    }

    public void record(String action, String entityType, String entityId, String description) {
        PortalPrincipal p = security.principal();
        events.save(new AuditEvent(p.name(), p.role(), action, entityType, entityId, description,
                request.getRemoteAddr(), String.valueOf(request.getAttribute("correlationId"))));
    }
}
