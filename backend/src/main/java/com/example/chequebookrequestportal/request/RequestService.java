package com.example.chequebookrequestportal.request;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.BusinessIdentifier;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.corebanking.dto.request.InternalTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.ChequeBookChargePosting;
import com.example.chequebookrequestportal.corebanking.dto.response.InternalTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.domain.ChargeTransaction;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.ChequeNumber;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.EligibilityRecord;
import com.example.chequebookrequestportal.domain.RequestHistory;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
import com.example.chequebookrequestportal.repository.ChargeTransactionRepository;
import com.example.chequebookrequestportal.repository.BranchRepository;
import com.example.chequebookrequestportal.repository.ChequeRequestRepository;
import com.example.chequebookrequestportal.repository.ChequeNumberRepository;
import com.example.chequebookrequestportal.repository.EligibilityRepository;
import com.example.chequebookrequestportal.repository.RequestHistoryRepository;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import com.example.chequebookrequestportal.security.SecurityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestService {
    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    private static final Set<RequestStatus> TERMINAL = EnumSet.of(RequestStatus.REJECTED, RequestStatus.DELIVERED,
            RequestStatus.CANCELLED);
    private final ChequeRequestRepository requests;
    private final EligibilityRepository eligibility;
    private final RequestHistoryRepository history;
    private final ChargeTransactionRepository charges;
    private final BranchRepository branches;
    private final ChequeNumberRepository chequeNumbers;
    private final CoreBankingService coreBanking;
    private final SecurityService security;
    private final AuditService audit;
    private final WorkflowEmailService emailNotifications;
    private final BigDecimal charge50;
    private final BigDecimal charge100;
    private final String chargeCreditAccount;
    private final BigDecimal vatRate;
    private final String defaultCurrency;

    public RequestService(ChequeRequestRepository r, EligibilityRepository e, RequestHistoryRepository h,
            ChargeTransactionRepository c, BranchRepository branches, ChequeNumberRepository chequeNumbers,
            CoreBankingService coreBanking,
            SecurityService s, AuditService a, WorkflowEmailService emailNotifications,
            @Value("${app.charges.leaves-50}") BigDecimal charge50,
            @Value("${app.charges.leaves-100}") BigDecimal charge100,
            @Value("${core-banking.chequebook-credit-account}") String chargeCreditAccount,
            @Value("${vat.rate}") BigDecimal vatRate,
            @Value("${core-banking.transaction-currency:NGN}") String defaultCurrency) {
        requests = r;
        eligibility = e;
        history = h;
        charges = c;
        this.branches = branches;
        this.chequeNumbers = chequeNumbers;
        this.coreBanking = coreBanking;
        security = s;
        audit = a;
        this.emailNotifications = emailNotifications;
        this.charge50 = charge50;
        this.charge100 = charge100;
        this.chargeCreditAccount = chargeCreditAccount;
        if (vatRate == null || vatRate.signum() < 0)
            throw new IllegalArgumentException("vat.rate must be zero or greater");
        this.vatRate = vatRate;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public Map<String, Object> create(String accountNumber, int leafCount, String chequeTypeCode) {
        security.requirePermission(Permissions.CREATE_REQUEST);
        if (leafCount != 50 && leafCount != 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEAF_COUNT", "Leaf count must be 50 or 100.");
        if (requests.existsByAccountNumberAndStatusNotIn(accountNumber, TERMINAL))
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REQUEST",
                    "An active chequebook request already exists for this account.");
        ChequeType chequeType;
        try {
            chequeType = ChequeType.fromCode(chequeTypeCode);
        }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHEQUE_TYPE",
                    "Cheque type must be 01 or 02.");
        }
        StaffUser actor = security.user();
        if (actor.getBranch() == null)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "USER_BRANCH_REQUIRED",
                    "The initiating user must be assigned to a branch.");
        NameEnquiryResponse account = coreBanking.nameEnquiry(accountNumber);
        requireEligibleAccount(account);
        String resolvedAccountNumber = valueOrDefault(account.accountNumber(), accountNumber);
        String accountHomeBranchCode = nullableValue(account.branchCode());
        String accountHomeBranchName = nullableValue(account.branchName());
        // The authenticated CSO's assigned branch owns approval, charging, fulfillment and collection.
        String branchCode = actor.getBranch().getCode();
        String branchName = actor.getBranch().getName();
        String branchAddress = actor.getBranch().getAddress();
        String sortCode = actor.getBranch().getSortCode();
        String currency = valueOrDefault(account.currency(), defaultCurrency);
        ChequeRequest request = new ChequeRequest(resolvedAccountNumber, account.accountName(), account.acctOwnership(),
                account.schemeCode(), chequeType, currency, accountHomeBranchCode, accountHomeBranchName,
                branchCode, branchName, branchAddress, sortCode, leafCount, actor);
        requests.save(request);
        request.assignRequestNumber(BusinessIdentifier.chequeRequest(
                LocalDate.now(ZoneOffset.UTC), branchCode, request.getId()));
        eligibility.save(new EligibilityRecord(request, true, account.accountStatus(), account.kycLevel(),
                account.restrictions(), null, account.status() == null ? null : account.status().code()));
        boolean crossBranch = accountHomeBranchCode != null && !sameSol(branchCode, accountHomeBranchCode);
        String creationNote = crossBranch
                ? "Cross-branch request created; Domicile Branch is "
                        + branchLabel(accountHomeBranchName, accountHomeBranchCode) + " and Initiating Branch is "
                        + branchLabel(branchName, branchCode) + "."
                : "Request created after core-banking name enquiry succeeded.";
        creationNote += " Cheque type: " + chequeType.displayName() + ".";
        history.save(new RequestHistory(request, null, request.getStatus().name(), actor.getFullName(), creationNote));
        audit.record("REQUEST_CREATED", "CHEQUE_REQUEST", request.getRequestNumber(),
                "Cheque book request created after successful name enquiry; cheque type "
                        + chequeType.displayName() + ".");
        if (crossBranch) {
            PortalPrincipal principal = security.principal();
            audit.record("CROSS_BRANCH_REQUEST_CREATED", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Cross-branch request initiated by " + actor.getFullName() + " (" + principal.role()
                            + "); Domicile Branch " + branchLabel(accountHomeBranchName, accountHomeBranchCode)
                            + "; Initiating Branch " + branchLabel(branchName, branchCode) + ".");
        }
        emailNotifications.requestCreated(request);
        return Map.of("request", view(request));
    }

    public Map<String, Object> list(String search, String status, String branch, String from, String to, int page,
            int pageSize, String sort) {
        return list(search, status, branch, from, to, page, pageSize, sort, true);
    }

    public Map<String, Object> listForReportExport(String branch, String from, int page, int pageSize) {
        security.requirePermission(Permissions.VIEW_REPORTS);
        return list("", "", branch, from, "", page, pageSize, "newest", false);
    }

    private Map<String, Object> list(String search, String status, String branch, String from, String to, int page,
            int pageSize, String sort, boolean maskAccountNumber) {
        security.requireAnyPermission(Permissions.VIEW_REQUEST, Permissions.VIEW_REPORTS);
        PortalPrincipal p = security.principal();
        Specification<ChequeRequest> spec = (root, q, cb) -> cb.conjunction();
        boolean branchScoped = p.roles().contains("CSO") || p.roles().contains("BOM");
        if (branchScoped) {
            if (p.branchCode() == null || p.branchCode().isBlank())
                throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_REQUIRED",
                        "Your user profile must be assigned to a branch.");
            if (branch != null && !branch.isBlank() && !"null".equals(branch)
                    && !sameSol(p.branchCode(), branch))
                throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                        "You cannot access cheque book requests for another branch.");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("branchCode"), p.branchCode()));
        }
        else if (branch != null && !branch.isBlank() && !"null".equals(branch))
            spec = spec.and((root, q, cb) -> cb.equal(root.get("branchCode"), branch));
        if (status != null && !status.isBlank()) {
            RequestStatus selected;
            try {
                selected = RequestStatus.valueOf(status);
            }
            catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown request status.");
            }
            RequestStatus selectedStatus = selected;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), selectedStatus));
        }
        if (search != null && !search.isBlank()) {
            String term = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("requestNumber")), term),
                    cb.like(cb.lower(root.get("accountName")), term),
                    cb.like(cb.lower(root.get("accountNumber")), term)));
        }
        if (from != null && !from.isBlank()) {
            Instant instant = parseDate(from, "from").atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), instant));
        }
        if (to != null && !to.isBlank()) {
            Instant instant = parseDate(to, "to").plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), instant));
        }
        Sort sorting = "oldest".equals(sort) ? Sort.by("createdAt").ascending()
                : "id".equals(sort) ? Sort.by("requestNumber").ascending() : Sort.by("createdAt").descending();
        var result = requests.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100), sorting));
        return Map.of("data", result.map(request -> summaryView(request, maskAccountNumber)).getContent(),
                "total", result.getTotalElements(), "page",
                result.getNumber(), "pageSize", result.getSize());
    }

    public Map<String, Object> get(String number) {
        security.requirePermission(Permissions.VIEW_REQUEST);
        ChequeRequest r = request(number);
        assertScope(r);
        return Map.of("request", view(r));
    }

    public List<Map<String, Object>> pending() {
        security.requirePermission(Permissions.APPROVE_REQUEST);
        PortalPrincipal principal = requireBOM();
        String branch = principal.branchCode();
        if (branch == null || branch.isBlank())
            return List.of();
        return requests.findByStatusAndBranchCodeIgnoreCaseOrderByCreatedAtAsc(
                RequestStatus.PENDING_APPROVAL, branch).stream().map(this::view).toList();
    }

    public List<Map<String, Object>> failedDebits() {
        security.requirePermission(Permissions.APPROVE_REQUEST);
        PortalPrincipal principal = requireBOM();
        String branch = principal.branchCode();
        if (branch == null || branch.isBlank())
            return List.of();
        return requests.findByStatusAndBranchCodeIgnoreCaseOrderByCreatedAtAsc(
                RequestStatus.DEBIT_FAILED, branch).stream().map(this::view).toList();
    }

    public List<Map<String, Object>> processingQueue() {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        List<ChequeRequest> queue = new ArrayList<>(requests.findProcessingQueue(
                EnumSet.of(RequestStatus.APPROVED, RequestStatus.EXPORTED, RequestStatus.FINACLE_REGISTERED,
                        RequestStatus.FINACLE_REGISTRATION_FAILED,
                        RequestStatus.FINACLE_REGISTRATION_REVIEW_REQUIRED),
                DebitStatus.SUCCESSFUL));
        queue.addAll(requests.findByStatusOrderByCreatedAtAsc(RequestStatus.REVERSAL_REVIEW_REQUIRED));
        queue.sort(Comparator.comparing(ChequeRequest::getCreatedAt));
        return queue.stream().map(this::view).toList();
    }

    public Map<String, Object> rejectIndividualPrinting(String number) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        request(number);
        throw new ApiException(HttpStatus.CONFLICT, "BATCH_PRINT_CONFIRMATION_REQUIRED",
                "Printing must be confirmed from the vendor export batch.");
    }

    @Transactional
    public Map<String, Object> reject(String number, String reason) {
        security.requirePermission(Permissions.APPROVE_REQUEST);
        ChequeRequest r = lockedRequest(number);
        StaffUser actor = security.user();
        assertBOM(r, actor);
        if (r.getStatus() != RequestStatus.PENDING_APPROVAL)
            invalidTransition(r);
        RequestStatus from = r.getStatus();
        r.reject(actor, reason);
        history.save(new RequestHistory(r, from.name(), r.getStatus().name(), actor.getFullName(), reason));
        audit.record("REQUEST_REJECTED", "CHEQUE_REQUEST", number, reason);
        return Map.of("request", view(r));
    }

    @Transactional
    public Map<String, Object> approve(String number) {
        security.requirePermission(Permissions.APPROVE_REQUEST);
        ChequeRequest r = lockedRequest(number);
        StaffUser actor = security.user();
        assertBOM(r, actor);
        if (r.getStatus() != RequestStatus.PENDING_APPROVAL)
            invalidTransition(r);
        Branch postingBranch = branchWithPostingAccounts(r);
        r.approve(actor);
        transition(r, RequestStatus.DEBIT_PENDING, actor, "BOM approved; customer debit initiated.");
        String key = "CB-" + r.getRequestNumber() + "-" + UUID.randomUUID();
        BigDecimal chargeAmount = chargeFor(r.getLeafCount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vatAmount = calculateVatCharge(chargeAmount);
        ChargeTransaction tx = charges.save(new ChargeTransaction(r, chargeAmount, vatAmount, vatRate,
                postingBranch.getChargeGlAccount(), postingBranch.getVatGlAccount(), r.getCurrency(), key, actor));
        performDebit(r, tx, actor);
        return Map.of("request", view(r));
    }

    @Transactional
    public Map<String, Object> reconcile(String number) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeRequest r = lockedRequest(number);
        ChargeTransaction tx = charges.findByRequestRequestNumber(number)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBIT_NOT_FOUND",
                        "No debit transaction exists for this request."));
        if (tx.getStatus() != DebitStatus.UNKNOWN && tx.getStatus() != DebitStatus.REVERSAL_UNKNOWN)
            throw new ApiException(HttpStatus.CONFLICT, "TRANSACTION_NOT_UNKNOWN",
                    "This transaction does not require status reconciliation.");
        throw new ApiException(HttpStatus.CONFLICT, "CORE_BANKING_STATUS_ENQUIRY_REQUIRED",
                "The configured core-banking package does not provide a transaction-status enquiry. "
                        + "Resolve the transfer status before retrying.");
    }

    @Transactional
    public Map<String, Object> retryDebit(String number) {
        security.requirePermission(Permissions.APPROVE_REQUEST);
        ChequeRequest r = lockedRequest(number);
        StaffUser actor = security.user();
        assertBOM(r, actor);
        ChargeTransaction tx = charges.findByRequestRequestNumber(number).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "DEBIT_NOT_FOUND", "No debit transaction exists."));
        if (tx.getStatus() != DebitStatus.FAILED || r.getStatus() != RequestStatus.DEBIT_FAILED)
            throw new ApiException(HttpStatus.CONFLICT, "DEBIT_NOT_RETRYABLE",
                    "Only a confirmed failed debit can be retried by the branch BOM.");
        tx.retryDebit();
        transition(r, RequestStatus.DEBIT_PENDING, actor,
                "BOM retried a confirmed failed customer debit.");
        performDebit(r, tx, actor);
        return Map.of("request", view(r));
    }

    @Transactional
    public Map<String, Object> retryReversal(String number) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeRequest r = lockedRequest(number);
        ChargeTransaction tx = charges.findByRequestRequestNumber(number).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "DEBIT_NOT_FOUND", "No debit transaction exists."));
        if (tx.getStatus() != DebitStatus.REVERSAL_FAILED
                || r.getStatus() != RequestStatus.REVERSAL_REVIEW_REQUIRED)
            throw new ApiException(HttpStatus.CONFLICT, "REVERSAL_NOT_RETRYABLE",
                    "Only a confirmed failed reversal can be retried.");
        reverseAfterChequeFailure(r, tx, security.user(), "Cheque book request was unsuccessful.",
                !chequeOutcomeRequiresReconciliation(r));
        return Map.of("request", view(r));
    }

    @Transactional
    public Map<String, Object> reconcileReversal(String number, ReversalReviewOutcome outcome,
            String evidenceReference, String note) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeRequest request = lockedRequest(number);
        ChargeTransaction transaction = charges.findByRequestRequestNumber(number).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "DEBIT_NOT_FOUND", "No debit transaction exists."));
        if (request.getStatus() != RequestStatus.REVERSAL_REVIEW_REQUIRED
                || transaction.getStatus() != DebitStatus.REVERSAL_UNKNOWN)
            throw new ApiException(HttpStatus.CONFLICT, "REVERSAL_NOT_RECONCILABLE",
                    "Only a reversal with an unknown outcome can be reconciled.");

        StaffUser actor = security.user();
        String reference = evidenceReference.trim();
        String reviewNote = note.trim();
        String evidence = boundedHistoryNote("Evidence " + reference + ". " + reviewNote);

        if (outcome == ReversalReviewOutcome.REVERSED) {
            if (chequeOutcomeRequiresReconciliation(request))
                throw new ApiException(HttpStatus.CONFLICT, "CHEQUE_NUMBER_RECONCILIATION_REQUIRED",
                        "The cheque-book host outcome must be reconciled before this request can be cancelled.");
            transaction.reconcileUnknownReversal(DebitStatus.REVERSED, null);
            transition(request, RequestStatus.CANCELLED, actor,
                    boundedHistoryNote("Operations confirmed that the customer charge was reversed. " + evidence));
            audit.record("CUSTOMER_REVERSAL_RECONCILED", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Operations confirmed reversal using evidence " + reference + ".");
        }
        else {
            transaction.reconcileUnknownReversal(DebitStatus.REVERSAL_FAILED, reviewNote);
            history.save(new RequestHistory(request, request.getStatus().name(), request.getStatus().name(),
                    actor.getFullName(), boundedHistoryNote(
                            "Operations confirmed that the reversal was not completed. " + evidence)));
            audit.record("CUSTOMER_REVERSAL_NOT_COMPLETED", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Operations confirmed that reversal was not completed using evidence " + reference + ".");
        }
        return Map.of("request", view(request));
    }

    @Transactional
    public Map<String, Object> transition(String number, RequestStatus expected, RequestStatus next, String note) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeRequest r = lockedRequest(number);
        if (r.getStatus() != expected)
            invalidTransition(r);
        if (!charges.existsByRequestRequestNumberAndStatus(number, DebitStatus.SUCCESSFUL))
            throw new ApiException(HttpStatus.CONFLICT, "DEBIT_NOT_SUCCESSFUL",
                    "Only a request with a successful debit can be processed by Clearing Operations.");
        transition(r, next, security.user(), note);
        return Map.of("request", view(r));
    }

    @Transactional
    public Map<String, Object> receiveAtBranch(String number) {
        security.requirePermission(Permissions.DELIVER_REQUEST);
        ChequeRequest request = lockedRequest(number);
        PortalPrincipal principal = security.principal();
        assertServicingBranch(principal, request);
        if (request.getStatus() != RequestStatus.DISPATCHED)
            invalidTransition(request);
        transition(request, RequestStatus.RECEIVED_BY_BRANCH, security.user(),
                "Physical receipt confirmed by servicing branch CSO.");
        return Map.of("request", view(request));
    }

    @Transactional
    public Map<String, Object> deliver(String number, DeliveryConfirmationRequest confirmation) {
        security.requirePermission(Permissions.DELIVER_REQUEST);
        ChequeRequest request = lockedRequest(number);
        PortalPrincipal principal = security.principal();
        assertServicingBranch(principal, request);
        if (request.getStatus() != RequestStatus.RECEIVED_BY_BRANCH)
            invalidTransition(request);
        if (confirmation == null || confirmation.requestVersion() == null
                || request.getVersion() != confirmation.requestVersion())
            throw new ApiException(HttpStatus.CONFLICT, "STALE_REQUEST_VERSION",
                    "This request has changed. Refresh it before confirming delivery.");
        if (!Boolean.TRUE.equals(confirmation.identityVerified())
                || !Boolean.TRUE.equals(confirmation.handoverConfirmed()))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DELIVERY_CONFIRMATION_REQUIRED",
                    "Customer identity and cheque book handover must both be confirmed.");
        String accountLastFour = confirmation.accountLastFour();
        if (accountLastFour == null || !accountLastFour.matches("\\d{4}"))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ACCOUNT_SUFFIX",
                    "Account number confirmation must contain exactly four digits.");
        String accountNumber = request.getAccountNumber();
        if (accountNumber == null || accountNumber.length() < 4)
            throw new IllegalStateException("The request account number cannot be verified.");
        if (!accountNumber.endsWith(accountLastFour))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_SUFFIX_MISMATCH",
                    "The delivery details could not be verified.");
        transition(request, RequestStatus.DELIVERED, security.user(),
                "Customer identity and physical cheque-book handover confirmed by branch CSO.");
        return Map.of("request", view(request));
    }

    private void assertServicingBranch(PortalPrincipal principal, ChequeRequest request) {
        if (principal.branchCode() == null || principal.branchCode().isBlank())
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_REQUIRED",
                    "Your user profile must be assigned to a branch.");
        if (!sameSol(principal.branchCode(), request.getBranchCode()))
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                    "This request belongs to another branch.");
    }

    private void performDebit(ChequeRequest request, ChargeTransaction transaction, StaffUser actor) {
        InternalTransferResponse debit;
        try {
            log.info("Posting cheque book charge for request {}: charge={}, VAT={}, total={}",
                    request.getRequestNumber(), transaction.getChargeAmount(), transaction.getVatAmount(),
                    transaction.getAmount());
            debit = postCharge(request, transaction, false);
        }
        catch (ApiException exception) {
            transaction.completeDebit(DebitStatus.UNKNOWN, null, exception.getCode(), exception.getMessage());
            transition(request, RequestStatus.DEBIT_REVIEW_REQUIRED, actor,
                    "Debit result is unknown; core-banking status enquiry is required.");
            audit.record("CUSTOMER_DEBIT_UNKNOWN", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Core-banking transfer outcome is unknown.");
            return;
        }

        if (!debit.successful()) {
            String failure = valueOrDefault(debit.responseMessage(), "Core banking rejected the debit.");
            transaction.completeDebit(DebitStatus.FAILED, debit.transactionReference(), debit.responseCode(), failure);
            transition(request, RequestStatus.DEBIT_FAILED, actor, failure);
            audit.record("CUSTOMER_DEBIT_FAILED", "CHEQUE_REQUEST", request.getRequestNumber(), failure);
            return;
        }
        transaction.completeDebit(DebitStatus.SUCCESSFUL, debit.transactionReference(), debit.responseCode(), null);
        audit.record("CUSTOMER_DEBIT_SUCCESSFUL", "CHEQUE_REQUEST", request.getRequestNumber(),
                "Cheque book charge transferred successfully.");
        transition(request, RequestStatus.APPROVED, actor,
                "Customer debit completed successfully; request is ready for vendor export.");
    }

    private void reverseAfterChequeFailure(ChequeRequest request, ChargeTransaction transaction, StaffUser actor,
            String chequeFailure, boolean releaseAfterSuccessfulReversal) {
        transaction.beginReversal();
        audit.record("CHEQUEBOOK_REQUEST_FAILED", "CHEQUE_REQUEST", request.getRequestNumber(), chequeFailure);
        try {
            InternalTransferResponse reversal = postCharge(request, transaction, true);
            if (reversal.successful()) {
                transaction.completeReversal(DebitStatus.REVERSED, null);
                RequestStatus resolvedStatus = releaseAfterSuccessfulReversal
                        ? RequestStatus.CANCELLED
                        : RequestStatus.CHEQUEBOOK_REQUEST_FAILED;
                String resolutionNote = releaseAfterSuccessfulReversal
                        ? "Cheque book request failed, the customer charge was reversed, and the request was cancelled."
                        : "Customer charge was reversed, but the cheque-book host outcome still requires reconciliation.";
                transition(request, resolvedStatus, actor, resolutionNote);
                audit.record("CUSTOMER_DEBIT_REVERSED", "CHEQUE_REQUEST", request.getRequestNumber(),
                        "Customer charge reversed after cheque book request failure.");
            }
            else {
                String failure = valueOrDefault(reversal.responseMessage(), "Core banking rejected the reversal.");
                transaction.completeReversal(DebitStatus.REVERSAL_FAILED, failure);
                transition(request, RequestStatus.REVERSAL_REVIEW_REQUIRED, actor,
                        "Cheque book request failed and charge reversal was unsuccessful.");
                audit.record("CUSTOMER_REVERSAL_FAILED", "CHEQUE_REQUEST", request.getRequestNumber(), failure);
            }
        }
        catch (ApiException exception) {
            transaction.completeReversal(DebitStatus.REVERSAL_UNKNOWN, exception.getMessage());
            transition(request, RequestStatus.REVERSAL_REVIEW_REQUIRED, actor,
                    "Cheque book request failed and the reversal outcome is unknown.");
            audit.record("CUSTOMER_REVERSAL_UNKNOWN", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Core-banking reversal requires status enquiry.");
        }
    }

    private InternalTransferResponse postCharge(ChequeRequest request, ChargeTransaction transaction,
            boolean reversal) {
        if (transaction.hasVatBreakdown() && transaction.getVatAmount().signum() > 0) {
            ChequeBookChargePosting posting = new ChequeBookChargePosting(
                    request.getAccountNumber(), transaction.getChargeGlAccount(), transaction.getVatGlAccount(),
                    transaction.getChargeAmount(), transaction.getVatAmount(),
                    (reversal ? "REVERSAL CHEQUEBOOK " : "CHEQUEBOOK ") + request.getRequestNumber());
            return reversal ? coreBanking.reverseChequeBookCharge(posting)
                    : coreBanking.postChequeBookCharge(posting);
        }
        // Existing charge records have no VAT breakdown and retain their original two-leg reversal.
        return coreBanking.transfer(new InternalTransferRequest(
                reversal ? chargeCreditAccount : request.getAccountNumber(),
                reversal ? request.getAccountNumber() : chargeCreditAccount,
                transaction.getAmount(), request.getRequestNumber(),
                reversal ? "REVERSAL - CHEQUE BOOK ISSUANCE FEE" : "CHEQUE BOOK ISSUANCE FEE",
                (reversal ? "REVERSAL CHEQUEBOOK " : "CHEQUEBOOK ") + request.getRequestNumber()));
    }

    private BigDecimal chargeFor(int leafCount) {
        return leafCount == 50 ? charge50 : charge100;
    }

    private BigDecimal calculateVatCharge(BigDecimal chargeAmount) {
        return chargeAmount.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean chequeOutcomeRequiresReconciliation(ChequeRequest request) {
        ChequeNumber counter = chequeNumbers.findForUpdate(ChequeNumber.COUNTER_ID)
                .orElseThrow(() -> new IllegalStateException("Cheque number counter has not been initialized"));
        return counter.isReconciliationRequired()
                && request.getRequestNumber().equals(counter.getReconciliationRequestNumber());
    }

    private String boundedHistoryNote(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private Branch branchWithPostingAccounts(ChequeRequest request) {
        Branch branch = branches.findByCodeIgnoreCase(request.getBranchCode())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REQUEST_BRANCH_NOT_FOUND",
                        "The request branch is no longer configured."));
        if (branch.getChargeGlAccount() == null || branch.getChargeGlAccount().isBlank()
                || branch.getVatGlAccount() == null || branch.getVatGlAccount().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BRANCH_GL_NOT_CONFIGURED",
                    "Charge GL and VAT GL accounts must be configured for branch " + branch.getCode()
                            + " before approval.");
        }
        if (branch.getChargeGlAccount().equalsIgnoreCase(branch.getVatGlAccount())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BRANCH_GL_ACCOUNTS_MUST_DIFFER",
                    "The branch charge GL and VAT GL accounts must be different.");
        }
        return branch;
    }

    private void transition(ChequeRequest r, RequestStatus next, StaffUser actor, String note) {
        String from = r.getStatus().name();
        r.setStatus(next);
        history.save(new RequestHistory(r, from, next.name(), actor.getFullName(), note));
        audit.record("REQUEST_STATUS_CHANGED", "CHEQUE_REQUEST", r.getRequestNumber(), from + " -> " + next.name());
        emailNotifications.requestTransitioned(r, next);
    }

    private void assertBOM(ChequeRequest r, StaffUser actor) {
        PortalPrincipal principal = requireBOM();
        if (r.getCreatedBy().getId().equals(actor.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "MAKER_CHECKER_VIOLATION",
                    "The maker cannot approve their own request.");
        if (actor.getBranch() == null || principal.branchCode() == null || principal.branchCode().isBlank())
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_REQUIRED",
                    "A BOM must be assigned to a branch to perform this action.");
        if (!actor.getBranch().getCode().equalsIgnoreCase(r.getBranchCode())
                || !principal.branchCode().equalsIgnoreCase(r.getBranchCode()))
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                    "This request belongs to another branch.");
    }

    private PortalPrincipal requireBOM() {
        PortalPrincipal principal = security.principal();
        if (!principal.roles().contains("BOM"))
            throw new ApiException(HttpStatus.FORBIDDEN, "BOM_ROLE_REQUIRED",
                    "Only a Branch Operations Manager can perform this action.");
        return principal;
    }

    private void assertScope(ChequeRequest r) {
        PortalPrincipal p = security.principal();
        if ((p.roles().contains("CSO") || p.roles().contains("BOM")) && !r.getBranchCode().equals(p.branchCode()))
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                    "This request belongs to another branch.");
    }

    private ChequeRequest request(String number) {
        return requests.findByRequestNumber(number)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Request not found."));
    }

    private ChequeRequest lockedRequest(String number) {
        return requests.findByRequestNumberForUpdate(number)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Request not found."));
    }

    private void invalidTransition(ChequeRequest r) {
        throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                "This action is not allowed from " + r.getStatus() + ".");
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        }
        catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", field + " must use YYYY-MM-DD format.");
        }
    }

    private Map<String, Object> summaryView(ChequeRequest request, boolean maskAccountNumber) {
        Map<String, Object> result = new LinkedHashMap<>(view(request));
        result.put("accountNumber", maskAccountNumber ? maskAccountNumber(request.getAccountNumber())
                : request.getAccountNumber());
        return result;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}"))
            return "**********";
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(7);
    }

    public Map<String, Object> view(ChequeRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getRequestNumber());
        m.put("customer", r.getAccountName());
        m.put("accountNumber", r.getAccountNumber());
        m.put("acctOwnership", r.getacctOwnership());
        m.put("chequeTypeCode", r.getChequeType() == null ? null : r.getChequeType().code());
        m.put("chequeTypeLabel", r.getChequeType() == null ? null : r.getChequeType().label());
        m.put("chequeType", r.getChequeType() == null ? null : r.getChequeType().displayName());
        m.put("branch", r.getBranchName());
        m.put("branchCode", r.getBranchCode());
        m.put("servicingBranch", r.getBranchName());
        m.put("servicingBranchName", r.getBranchName());
        m.put("servicingBranchCode", r.getBranchCode());
        m.put("accountHomeBranch", branchLabel(r.getAccountHomeBranchName(), r.getAccountHomeBranchCode()));
        m.put("accountHomeBranchName", r.getAccountHomeBranchName());
        m.put("accountHomeBranchCode", r.getAccountHomeBranchCode());
        m.put("createdAt", r.getCreatedAt());
        m.put("status", r.getStatus());
        m.put("createdBy", r.getCreatedBy().getFullName());
        m.put("leafCount", r.getLeafCount());
        m.put("bookletCount", r.getBookletCount());
        m.put("version", r.getVersion());
        if (r.getSerialAllocation() != null) {
            var allocation = r.getSerialAllocation();
            m.put("startChequeNumber", allocation.getStartChequeNumber());
            m.put("endChequeNumber", allocation.getEndChequeNumber());
            m.put("vendorReference", allocation.getVendorReference());
            m.put("printStatus", allocation.getPrintStatus());
            m.put("finacleRegistrationStatus", allocation.getFinacleRegistrationStatus());
            m.put("finacleHostReference", allocation.getFinacleHostReference());
            m.put("finacleRegistrationFailureReason", allocation.getFailureReason());
        }
        charges.findByRequestRequestNumber(r.getRequestNumber()).ifPresent(c -> {
            m.put("debitStatus", c.getStatus());
            m.put("chargeAmount", c.getChargeAmount());
            if (c.hasVatBreakdown()) {
                m.put("vatRate", c.getVatRate());
                m.put("vatAmount", c.getVatAmount());
                m.put("totalDebit", c.getAmount());
            }
            m.put("currency", c.getCurrency());
            m.put("debitFailureReason", c.getFailureReason());
        });
        m.put("timeline",
                history.findByRequestRequestNumberOrderByCreatedAtAsc(r.getRequestNumber()).stream()
                        .map(h -> Map.of("status", h.getToStatus().replace('_', ' '), "at", h.getCreatedAt(), "actor",
                                h.getActor(), "note", h.getNote() == null ? "" : h.getNote()))
                        .toList());
        return m;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullableValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireEligibleAccount(NameEnquiryResponse account) {
        boolean eligible = account.accountName() != null && !account.accountName().isBlank();
        if (account.accountStatus() != null && !account.accountStatus().isBlank())
            eligible &= "ACTIVE".equalsIgnoreCase(account.accountStatus());
        if (account.kycLevel() != null && !account.kycLevel().isBlank())
            eligible &= "3".equalsIgnoreCase(account.kycLevel());
        if (account.restrictions() != null && !account.restrictions().isBlank())
            eligible &= "NONE".equalsIgnoreCase(account.restrictions())
                    || "NO".equalsIgnoreCase(account.restrictions());
        if (!eligible)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ELIGIBLE",
                    "The account failed one or more eligibility checks.");
    }

    private String branchLabel(String name, String code) {
        if (name != null && !name.isBlank() && code != null && !code.isBlank())
            return name + " - " + code;
        if (name != null && !name.isBlank())
            return name;
        return code == null || code.isBlank() ? "Not provided" : code;
    }

    private boolean sameSol(String left, String right) {
        if (left == null || right == null)
            return false;
        return normalizeSol(left).equalsIgnoreCase(normalizeSol(right));
    }

    private String normalizeSol(String value) {
        String trimmed = value.trim();
        return trimmed.matches("\\d+") ? trimmed.replaceFirst("^0+(?!$)", "") : trimmed;
    }
}
