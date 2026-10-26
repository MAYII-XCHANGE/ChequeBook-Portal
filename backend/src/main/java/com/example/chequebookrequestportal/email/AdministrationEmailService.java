package com.example.chequebookrequestportal.email;

import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.EmailOutboxRepository;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministrationEmailService {
    private static final DateTimeFormatter CHANGE_TIME = DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a", Locale.forLanguageTag("en-NG"))
            .withZone(ZoneId.of("Africa/Lagos"));

    private final StaffUserRepository users;
    private final EmailOutboxRepository outbox;
    private final AdministrationEmailTemplateRenderer templateRenderer;
    private final boolean enabled;
    private final String portalUrl;
    private final String subjectPrefix;

    public AdministrationEmailService(StaffUserRepository users, EmailOutboxRepository outbox,
            AdministrationEmailTemplateRenderer templateRenderer,
            @Value("${app.email.enabled}") boolean enabled,
            @Value("${app.email.portal-base-url}") String portalUrl,
            @Value("${app.email.subject-prefix}") String subjectPrefix) {
        this.users = users;
        this.outbox = outbox;
        this.templateRenderer = templateRenderer;
        this.enabled = enabled;
        this.portalUrl = portalUrl.replaceAll("/+$", "");
        this.subjectPrefix = subjectPrefix.trim();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void branchModified(Branch branch, List<String> changes, String changedBy) {
        if (!enabled || changes.isEmpty())
            return;
        String reference = "BRANCH-" + branch.getCode();
        queue(EmailEventType.ADMIN_BRANCH_MODIFIED, reference, Set.of(branch.getCode()),
                "Branch Profile Updated",
                "An administrator changed branch information in the Chequebook Request Portal.",
                "Branch", branch.getName() + " (" + branch.getCode() + ")", changes, changedBy);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void userModified(StaffUser user, Set<String> affectedBranchCodes, List<String> changes,
            String changedBy) {
        if (!enabled || changes.isEmpty() || affectedBranchCodes.isEmpty())
            return;
        String employeeReference = user.getEmployeeId() == null || user.getEmployeeId().isBlank()
                ? String.valueOf(user.getId())
                : user.getEmployeeId();
        queue(EmailEventType.ADMIN_USER_MODIFIED, "USER-" + user.getId(), affectedBranchCodes,
                "Portal User Profile Updated",
                "An administrator changed a portal user assigned to your branch.",
                "User", user.getFullName() + " (" + employeeReference + ")", changes, changedBy);
    }

    private void queue(EmailEventType eventType, String reference, Set<String> branchCodes, String title,
            String message, String entityType, String entityReference, List<String> changes, String changedBy) {
        Set<String> normalizedBranchCodes = new LinkedHashSet<>();
        List<StaffUser> recipients = new ArrayList<>();
        for (String branchCode : branchCodes) {
            if (branchCode == null || branchCode.isBlank())
                continue;
            String normalized = branchCode.trim().toUpperCase(Locale.ROOT);
            if (normalizedBranchCodes.add(normalized))
                recipients.addAll(users.findActiveByRoleAndBranchIncludingInactiveBranch("BOM", normalized));
        }

        Set<String> queuedAddresses = new HashSet<>();
        String branches = String.join(", ", normalizedBranchCodes);
        String changedAt = CHANGE_TIME.format(Instant.now()) + " WAT";
        for (StaffUser recipient : recipients) {
            String address = recipient.getEmail() == null ? "" : recipient.getEmail().trim();
            if (address.isBlank() || !queuedAddresses.add(address.toLowerCase(Locale.ROOT)))
                continue;
            String body = templateRenderer.render(new AdministrationEmailTemplateModel(
                    recipient.getFullName(), title, message, entityType, entityReference, branches,
                    List.copyOf(changes), changedBy, changedAt, portalUrl));
            outbox.save(new EmailOutboxMessage(UUID.randomUUID().toString(), eventType, reference, address,
                    recipient.getFullName(), null, subjectPrefix + " " + title + " - " + reference, body));
        }
    }
}
