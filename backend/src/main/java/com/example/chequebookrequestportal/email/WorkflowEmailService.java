package com.example.chequebookrequestportal.email;

import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.EmailOutboxRepository;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowEmailService {
    private final StaffUserRepository users;
    private final EmailOutboxRepository outbox;
    private final WorkflowEmailTemplateRenderer templateRenderer;
    private final boolean enabled;
    private final String portalUrl;
    private final String subjectPrefix;
    private final String operationsCcAddress;

    public WorkflowEmailService(StaffUserRepository users, EmailOutboxRepository outbox,
            WorkflowEmailTemplateRenderer templateRenderer,
            @Value("${app.email.enabled}") boolean enabled,
            @Value("${app.email.portal-base-url}") String portalUrl,
            @Value("${app.email.subject-prefix}") String subjectPrefix,
            @Value("${app.email.clearing-operations-cc:}") String operationsCcAddress) {
        this.users = users;
        this.outbox = outbox;
        this.templateRenderer = templateRenderer;
        this.enabled = enabled;
        this.portalUrl = portalUrl.replaceAll("/+$", "");
        this.subjectPrefix = subjectPrefix.trim();
        this.operationsCcAddress = operationsCcAddress.trim();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestCreated(ChequeRequest request) {
        if (!enabled)
            return;
        queue(request, EmailEventType.APPROVAL_REQUIRED,
                users.findActiveByRoleAndBranch("BOM", request.getBranchCode()),
                "Chequebook Request Awaiting Approval",
                "A new chequebook request is awaiting your review and approval.", RequestStatus.PENDING_APPROVAL, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestTransitioned(ChequeRequest request, RequestStatus status) {
        if (!enabled)
            return;
        switch (status) {
            case APPROVED -> queueOperations(request, EmailEventType.OPERATIONS_EXPORT_READY,
                    "Chequebook Request Ready for Vendor Export",
                    "A successfully debited chequebook request is ready for vendor export.", status);
            case DEBIT_REVIEW_REQUIRED -> queueOperations(request,
                    EmailEventType.OPERATIONS_DEBIT_REVIEW_REQUIRED,
                    "Debit Outcome Requires Review",
                    "A chequebook request has an unknown debit outcome and requires reconciliation.", status);
            case REVERSAL_REVIEW_REQUIRED -> queueOperations(request,
                    EmailEventType.OPERATIONS_REVERSAL_REVIEW_REQUIRED,
                    "Charge Reversal Requires Review",
                    "A chequebook charge reversal requires Operations review.", status);
            case FINACLE_REGISTRATION_FAILED, FINACLE_REGISTRATION_REVIEW_REQUIRED -> queueOperations(request,
                    EmailEventType.OPERATIONS_FINACLE_REGISTRATION_REVIEW,
                    "Finacle Cheque Registration Requires Attention",
                    "A vendor-printed chequebook requires Operations attention before it can be dispatched.", status);
            case CANCELLED -> queueBranch(request, EmailEventType.REQUEST_CANCELLED_AFTER_REVERSAL,
                    "Chequebook Request Cancelled",
                    "The failed chequebook request was cancelled after the customer charge was reversed. A new request may now be initiated if required.",
                    status);
            case DISPATCHED -> queueBranch(request, EmailEventType.BRANCH_DISPATCHED,
                    "Chequebook Dispatched to Branch",
                    "A chequebook has been dispatched to your branch.", status);
            case RECEIVED_BY_BRANCH -> {
                queueBranch(request, EmailEventType.BRANCH_RECEIVED,
                        "Chequebook Received at Branch",
                        "Your branch confirmed receipt of a chequebook that is now awaiting customer collection.",
                        status);
                queueOperations(request, EmailEventType.BRANCH_RECEIVED,
                        "Branch Receipt Confirmed",
                        "The servicing branch confirmed physical receipt of a dispatched chequebook.", status);
            }
            case DELIVERED -> queueOperations(request, EmailEventType.CUSTOMER_COLLECTION_CONFIRMED,
                    "Customer Chequebook Collection Confirmed",
                    "The servicing branch confirmed that the customer has collected the cheque book.", status);
            default -> {
                // DEBIT_FAILED is deliberately excluded: retry reminders remain in-app only.
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void chequeEligibilityReviewRequired(String accountNumber, String returnedFlag) {
        if (!enabled) return;
        String reference = "ELIG-" + hash(accountNumber).substring(0, 12).toUpperCase(Locale.ROOT);
        String title = "Cheque Eligibility Review Required";
        String message = "Finacle returned an unexpected cheque-eligibility flag (" + returnedFlag
                + ") for an account. Review the core-banking data before another cheque-book request is attempted.";
        Set<String> queuedAddresses = new HashSet<>();
        for (StaffUser recipient : users.findActiveByRole("OPERATIONS")) {
            String address = recipient.getEmail() == null ? "" : recipient.getEmail().trim();
            if (address.isBlank() || !queuedAddresses.add(address.toLowerCase(Locale.ROOT))) continue;
            String key = hash(EmailEventType.OPERATIONS_CHEQUE_ELIGIBILITY_REVIEW + "|" + reference + "|"
                    + LocalDate.now(ZoneOffset.UTC) + "|" + address.toLowerCase(Locale.ROOT));
            if (outbox.existsByDeduplicationKey(key)) continue;
            String body = templateRenderer.render(new WorkflowEmailTemplateModel(
                    recipient.getFullName(), title, message, reference, "Core Banking",
                    "Account eligibility check", mask(accountNumber), "Operations Review Required", portalUrl));
            outbox.save(new EmailOutboxMessage(key, EmailEventType.OPERATIONS_CHEQUE_ELIGIBILITY_REVIEW,
                    reference, address, recipient.getFullName(), operationsCcAddress,
                    subjectPrefix + " " + title + " - " + reference, body));
        }
    }

    private void queueOperations(ChequeRequest request, EmailEventType eventType, String title, String message,
            RequestStatus status) {
        queue(request, eventType, users.findActiveByRole("OPERATIONS"), title, message, status,
                operationsCcAddress);
    }

    private void queueBranch(ChequeRequest request, EmailEventType eventType, String title, String message,
            RequestStatus status) {
        queue(request, eventType, users.findActiveByRoleAndBranch("CSO", request.getBranchCode()), title, message,
                status, null);
    }

    private void queue(ChequeRequest request, EmailEventType eventType, List<StaffUser> recipients, String title,
            String message, RequestStatus status, String ccAddress) {
        Set<String> queuedAddresses = new HashSet<>();
        for (StaffUser recipient : recipients) {
            String address = recipient.getEmail() == null ? "" : recipient.getEmail().trim();
            if (address.isBlank() || !queuedAddresses.add(address.toLowerCase(Locale.ROOT)))
                continue;
            String key = hash(eventType + "|" + request.getRequestNumber() + "|" + request.getVersion() + "|"
                    + address.toLowerCase(Locale.ROOT));
            if (outbox.existsByDeduplicationKey(key))
                continue;
            String body = templateRenderer.render(new WorkflowEmailTemplateModel(
                    recipient.getFullName(), title, message, request.getRequestNumber(),
                    request.getBranchName() + " (" + request.getBranchCode() + ")",
                    request.getAccountName(), mask(request.getAccountNumber()), statusLabel(status), portalUrl));
            outbox.save(new EmailOutboxMessage(key, eventType, request.getRequestNumber(), address,
                    recipient.getFullName(), ccAddress,
                    subjectPrefix + " " + title + " - " + request.getRequestNumber(), body));
        }
    }

    private String statusLabel(RequestStatus status) {
        return switch (status) {
            case PENDING_APPROVAL -> "Awaiting BOM Review";
            case APPROVED -> "Ready for Vendor Export";
            case DEBIT_REVIEW_REQUIRED -> "Debit Review Required";
            case REVERSAL_REVIEW_REQUIRED -> "Reversal Review Required";
            case CANCELLED -> "Cancelled After Reversal";
            case DISPATCHED -> "Dispatched to Branch";
            case RECEIVED_BY_BRANCH -> "Awaiting Customer Collection";
            case DELIVERED -> "Delivered to Customer";
            default -> status.name().replace('_', ' ');
        };
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}"))
            return "**********";
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(7);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
