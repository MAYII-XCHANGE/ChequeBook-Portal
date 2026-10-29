package com.example.chequebookrequestportal.request;

import com.example.chequebookrequestportal.domain.RequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService s) {
        service = s;
    }

    @PostMapping("/cheque-requests")
    @PreAuthorize("hasAuthority('CREATE_CHEQUE_REQUEST')")
    Map<String, Object> create(@Valid @RequestBody CreateRequest b) {
        return service.create(b.accountNumber(), b.leafCount(), b.chequeType());
    }

    @GetMapping("/cheque-requests")
    @PreAuthorize("hasAuthority('VIEW_CHEQUE_REQUEST')")
    Map<String, Object> list(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String branch,
            @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "newest") String sort) {
        return service.list(search, status, branch, from, to, page, pageSize, sort);
    }

    @GetMapping("/cheque-requests/{number}")
    @PreAuthorize("hasAuthority('VIEW_CHEQUE_REQUEST')")
    Map<String, Object> get(@PathVariable String number) {
        return service.get(number);
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAuthority('BOM') and hasAuthority('APPROVE_CHEQUE_REQUEST')")
    Map<String, Object> pending() {
        return Map.of("data", service.pending());
    }

    @GetMapping("/approvals/debit-failures")
    @PreAuthorize("hasAuthority('BOM') and hasAuthority('APPROVE_CHEQUE_REQUEST')")
    Map<String, Object> failedDebits() {
        return Map.of("data", service.failedDebits());
    }

    @PostMapping("/cheque-requests/{number}/approve")
    @PreAuthorize("hasAuthority('BOM') and hasAuthority('APPROVE_CHEQUE_REQUEST')")
    Map<String, Object> approve(@PathVariable String number) {
        return service.approve(number);
    }

    @PostMapping("/cheque-requests/{number}/reject")
    @PreAuthorize("hasAuthority('BOM') and hasAuthority('APPROVE_CHEQUE_REQUEST')")
    Map<String, Object> reject(@PathVariable String number, @Valid @RequestBody RejectRequest b) {
        return service.reject(number, b.reason());
    }

    @PostMapping("/cheque-requests/{number}/debit/reconcile")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> reconcile(@PathVariable String number) {
        return service.reconcile(number);
    }

    @PostMapping("/cheque-requests/{number}/debit/retry")
    @PreAuthorize("hasAuthority('BOM') and hasAuthority('APPROVE_CHEQUE_REQUEST')")
    Map<String, Object> retryDebit(@PathVariable String number) {
        return service.retryDebit(number);
    }

    @PostMapping("/cheque-requests/{number}/reversal/retry")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> retryReversal(@PathVariable String number) {
        return service.retryReversal(number);
    }

    @PostMapping("/cheque-requests/{number}/reversal/reconcile")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> reconcileReversal(@PathVariable String number,
            @Valid @RequestBody ReversalReconciliationRequest request) {
        return service.reconcileReversal(number, request.outcome(), request.evidenceReference(), request.note());
    }

    @GetMapping("/processing/queue")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> queue() {
        return Map.of("data", service.processingQueue());
    }

    @PostMapping("/cheque-requests/{number}/mark-printed")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> printed(@PathVariable String number) {
        return service.rejectIndividualPrinting(number);
    }

    @PostMapping("/cheque-requests/{number}/dispatch")
    @PreAuthorize("hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> dispatch(@PathVariable String number) {
        return service.dispatch(number);
    }

    @PostMapping("/cheque-requests/{number}/receive-at-branch")
    @PreAuthorize("hasAuthority('DELIVER_CHEQUE_REQUEST')")
    Map<String, Object> receive(@PathVariable String number) {
        return service.receiveAtBranch(number);
    }

    @PostMapping("/cheque-requests/{number}/deliver")
    @PreAuthorize("hasAuthority('DELIVER_CHEQUE_REQUEST')")
    Map<String, Object> deliver(@PathVariable String number,
            @Valid @RequestBody DeliveryConfirmationRequest request) {
        return service.deliver(number, request);
    }

    @PostMapping("/cheque-requests/{number}/confirm-custody")
    @PreAuthorize("hasAuthority('DELIVER_CHEQUE_REQUEST')")
    Map<String, Object> confirmStockCustody(@PathVariable String number,
            @Valid @RequestBody ManagersChequeCustodyConfirmationRequest request) {
        return service.confirmManagersChequeCustody(number, request);
    }

    public record CreateRequest(
            @Pattern(regexp = "\\d{10}") String accountNumber,
            int leafCount,
            @NotBlank @Pattern(regexp = "01|02|03", message = "Cheque type must be 01, 02 or 03.") String chequeType) {
        @AssertTrue(message = "A 10-digit account number is required for cheque types 01 and 02.")
        public boolean isAccountNumberValidForChequeType() {
            return "03".equals(chequeType) || accountNumber != null && accountNumber.matches("\\d{10}");
        }
    }

    public record RejectRequest(@NotBlank @jakarta.validation.constraints.Size(min = 5, max = 1000) String reason) {
    }

    public record ReversalReconciliationRequest(
            @NotNull ReversalReviewOutcome outcome,
            @NotBlank @Size(max = 100) String evidenceReference,
            @NotBlank @Size(min = 10, max = 1000) String note) {
    }
}
