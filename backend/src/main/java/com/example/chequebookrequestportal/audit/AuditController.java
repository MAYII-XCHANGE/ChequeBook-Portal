package com.example.chequebookrequestportal.audit;

import com.example.chequebookrequestportal.domain.AuditEvent;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.repository.AuditEventRepository;
import com.example.chequebookrequestportal.security.SecurityService;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final AuditEventRepository events;
    private final SecurityService security;

    public AuditController(AuditEventRepository e, SecurityService security) {
        events = e;
        this.security = security;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_AUDIT_TRAIL')")
    Map<String, Object> list(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        security.requirePermission(Permissions.VIEW_AUDIT);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100),
                Sort.by("createdAt").descending());
        var result = search.isBlank() ? events.findAll(pageable)
                : events.findByActorContainingIgnoreCaseOrActionContainingIgnoreCaseOrEntityIdContainingIgnoreCase(
                        search, search, search, pageable);
        return Map.of("data", result.map(this::map).getContent(), "total", result.getTotalElements(), "page",
                result.getNumber(), "pageSize", result.getSize());
    }

    private Map<String, Object> map(AuditEvent e) {
        return Map.of("id", e.getId(), "at", e.getCreatedAt(), "user", e.getActor(), "role", e.getActorRole(), "action",
                e.getAction(), "requestId", e.getEntityId(), "detail", e.getDescription());
    }
}
