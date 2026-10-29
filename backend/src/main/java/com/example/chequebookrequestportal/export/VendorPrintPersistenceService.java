package com.example.chequebookrequestportal.export;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeSerialAllocation;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.ChequeTypeSequenceLock;
import com.example.chequebookrequestportal.domain.FinacleRegistrationStatus;
import com.example.chequebookrequestportal.domain.RequestHistory;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.domain.VendorExportBatch;
import com.example.chequebookrequestportal.domain.VendorExportItem;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
import com.example.chequebookrequestportal.repository.ChequeSerialAllocationRepository;
import com.example.chequebookrequestportal.repository.ChequeTypeSequenceLockRepository;
import com.example.chequebookrequestportal.repository.RequestHistoryRepository;
import com.example.chequebookrequestportal.repository.VendorExportBatchRepository;
import com.example.chequebookrequestportal.repository.VendorExportItemRepository;
import com.example.chequebookrequestportal.request.ChequeBookResponse;
import com.example.chequebookrequestportal.security.SecurityService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorPrintPersistenceService {
    private final VendorExportBatchRepository batches;
    private final VendorExportItemRepository items;
    private final ChequeSerialAllocationRepository allocations;
    private final ChequeTypeSequenceLockRepository typeLocks;
    private final RequestHistoryRepository history;
    private final SecurityService security;
    private final AuditService audit;
    private final WorkflowEmailService email;

    public VendorPrintPersistenceService(VendorExportBatchRepository batches, VendorExportItemRepository items,
            ChequeSerialAllocationRepository allocations,
            ChequeTypeSequenceLockRepository typeLocks, RequestHistoryRepository history, SecurityService security,
            AuditService audit, WorkflowEmailService email) {
        this.batches = batches;
        this.items = items;
        this.allocations = allocations;
        this.typeLocks = typeLocks;
        this.history = history;
        this.security = security;
        this.audit = audit;
        this.email = email;
    }

    @Transactional
    public ImportResult importRows(String batchNumber, List<VendorPrintResponseInput.Row> inputRows) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        security.requirePermission(Permissions.PROCESS_REQUEST);
        VendorExportBatch batch = batches.findByBatchNumberForUpdate(batchNumber).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "Vendor export batch not found."));
        if (!VendorExportBatch.STATUS_SENT.equals(batch.getStatus())
                && !VendorExportBatch.STATUS_PRINTED.equals(batch.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_BATCH_STATUS",
                    "Only a sent vendor batch can accept a print response.");

        List<VendorExportItem> batchItems = items.findByBatchBatchNumberOrderByIdAsc(batchNumber);
        List<ValidatedRow> rows = validateFile(batchNumber, inputRows, batchItems);

        rows.stream().map(ValidatedRow::chequeType).distinct().sorted().forEach(this::lockType);
        validateNoOverlaps(rows);

        StaffUser actor = security.user();
        List<String> registrations = new ArrayList<>();
        for (ValidatedRow row : rows) {
            ChequeRequest request = row.item().getRequest();
            var existing = allocations.findByRequestNumberForUpdate(request.getRequestNumber());
            if (existing.isPresent()) {
                ChequeSerialAllocation allocation = existing.get();
                if (!allocation.matches(row.chequeType(), row.start(), row.end(), row.leafCount(), row.vendorReference()))
                    throw new ApiException(HttpStatus.CONFLICT, "SERIAL_ALLOCATION_CONFLICT",
                            request.getRequestNumber() + " already has a different serial allocation.");
                if (allocation.getFinacleRegistrationStatus() == FinacleRegistrationStatus.NOT_STARTED)
                    registrations.add(request.getRequestNumber());
                continue;
            }

            if (request.getStatus() != RequestStatus.SENT_TO_VENDOR)
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                        request.getRequestNumber() + " is not waiting for a vendor response.");
            ChequeSerialAllocation allocation = allocations.save(new ChequeSerialAllocation(
                    request, batch, row.chequeType(), row.start(), row.end(), row.leafCount(),
                    row.vendorReference(), actor));
            request.attachSerialAllocation(allocation);
            transition(request, RequestStatus.PRINTED, actor,
                    "Vendor confirmed printing with serial range " + row.start() + "-" + row.end()
                            + " for cheque type " + row.chequeType() + ".");
            audit.record("CHEQUE_SERIAL_RANGE_ALLOCATED", "CHEQUE_REQUEST", request.getRequestNumber(),
                    "Batch " + batchNumber + "; cheque type " + row.chequeType() + "; vendor reference "
                            + row.vendorReference() + ".");
            registrations.add(request.getRequestNumber());
        }
        if (!VendorExportBatch.STATUS_PRINTED.equals(batch.getStatus()))
            batch.confirmPrinted();
        audit.record("VENDOR_PRINT_RESPONSE_IMPORTED", "VENDOR_EXPORT", batchNumber,
                "Imported and validated " + rows.size() + " vendor print response row(s).");
        return new ImportResult(batchNumber, rows.size(), registrations);
    }

    @Transactional
    public RegistrationCommand beginRegistration(String requestNumber, boolean retry) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeSerialAllocation allocation = allocationForUpdate(requestNumber);
        if (allocation.getFinacleRegistrationStatus() == FinacleRegistrationStatus.SUCCESSFUL)
            return null;
        if (retry) {
            if (allocation.getFinacleRegistrationStatus() != FinacleRegistrationStatus.FAILED)
                throw new ApiException(HttpStatus.CONFLICT, "FINACLE_REGISTRATION_NOT_RETRYABLE",
                        "Only a confirmed failed Finacle registration can be retried.");
        }
        else if (allocation.getFinacleRegistrationStatus() != FinacleRegistrationStatus.NOT_STARTED) {
            return null;
        }
        ChequeRequest request = allocation.getRequest();
        allocation.beginRegistration();
        transition(request, RequestStatus.FINACLE_REGISTRATION_PENDING, security.user(),
                retry ? "Retrying confirmed failed Finacle cheque registration."
                        : "Vendor serial range received; Finacle cheque registration started.");
        audit.record("FINACLE_REGISTRATION_STARTED", "CHEQUE_REQUEST", requestNumber,
                "Cheque type " + allocation.getChequeTypeCode() + "; vendor reference "
                        + allocation.getVendorReference() + ".");
        return new RegistrationCommand(requestNumber, request.getAccountNumber(), request.getLeafCount(),
                request.getBranchCode(), allocation.getStartChequeNumber());
    }

    @Transactional
    public void completeRegistration(String requestNumber, ChequeBookResponse response) {
        ChequeSerialAllocation allocation = allocationForUpdate(requestNumber);
        ChequeBookResponse.Data data = response.data();
        allocation.completeRegistration(data == null ? null : data.hostTransactionId(),
                data == null ? null : data.errorCode());
        transition(allocation.getRequest(), RequestStatus.FINACLE_REGISTERED, security.user(),
                "Finacle registered the vendor-assigned cheque serial range successfully.");
        audit.record("FINACLE_REGISTRATION_SUCCEEDED", "CHEQUE_REQUEST", requestNumber,
                "Finacle host reference " + (data == null ? "unavailable" : data.hostTransactionId()) + ".");
    }

    @Transactional
    public void failRegistration(String requestNumber, ChequeBookResponse response) {
        ChequeSerialAllocation allocation = allocationForUpdate(requestNumber);
        ChequeBookResponse.Data data = response.data();
        String code = data == null ? null : data.errorCode();
        String reason = response.failureMessage();
        allocation.failRegistration(code, reason);
        transition(allocation.getRequest(), RequestStatus.FINACLE_REGISTRATION_FAILED, security.user(), reason);
        audit.record("FINACLE_REGISTRATION_FAILED", "CHEQUE_REQUEST", requestNumber, reason);
    }

    @Transactional
    public void requireRegistrationReview(String requestNumber, String code, String reason) {
        ChequeSerialAllocation allocation = allocationForUpdate(requestNumber);
        allocation.requireReview(code, reason);
        transition(allocation.getRequest(), RequestStatus.FINACLE_REGISTRATION_REVIEW_REQUIRED, security.user(),
                "Finacle registration outcome is uncertain and requires independent verification.");
        audit.record("FINACLE_REGISTRATION_OUTCOME_UNCERTAIN", "CHEQUE_REQUEST", requestNumber, reason);
    }

    @Transactional
    public Map<String, Object> reconcile(String requestNumber, FinacleReconciliationInput input) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        ChequeSerialAllocation allocation = allocationForUpdate(requestNumber);
        if (allocation.getFinacleRegistrationStatus() != FinacleRegistrationStatus.REVIEW_REQUIRED)
            throw new ApiException(HttpStatus.CONFLICT, "FINACLE_REGISTRATION_NOT_RECONCILABLE",
                    "Only an uncertain Finacle registration can be reconciled.");
        String evidence = "Evidence " + input.evidenceReference().trim() + ". " + input.note().trim();
        if (input.outcome() == FinacleReconciliationInput.Outcome.REGISTERED) {
            allocation.reconcileAsRegistered();
            transition(allocation.getRequest(), RequestStatus.FINACLE_REGISTERED, security.user(),
                    "Operations confirmed Finacle registration. " + evidence);
        }
        else {
            allocation.reconcileAsNotRegistered(evidence);
            transition(allocation.getRequest(), RequestStatus.FINACLE_REGISTRATION_FAILED, security.user(),
                    "Operations confirmed that Finacle registration did not complete. " + evidence);
        }
        audit.record("FINACLE_REGISTRATION_RECONCILED", "CHEQUE_REQUEST", requestNumber,
                input.outcome() + "; evidence " + input.evidenceReference().trim() + ".");
        return allocationMap(allocation);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> batchAllocations(String batchNumber) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        return allocations.findByBatchBatchNumberOrderByIdAsc(batchNumber).stream().map(this::allocationMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> allocation(String requestNumber) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        return allocationMap(allocations.findByRequestRequestNumber(requestNumber).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "SERIAL_ALLOCATION_NOT_FOUND",
                        "No vendor serial allocation exists for this request.")));
    }

    private List<ValidatedRow> validateFile(String batchNumber, List<VendorPrintResponseInput.Row> inputRows,
            List<VendorExportItem> batchItems) {
        if (inputRows == null || inputRows.isEmpty())
            throw invalidRows(List.of("The response contains no rows."));
        List<String> errors = new ArrayList<>();
        Set<Integer> serialNumbers = new HashSet<>();
        List<ValidatedRow> result = new ArrayList<>();
        for (int index = 0; index < inputRows.size(); index++) {
            int rowNumber = index + 2;
            VendorPrintResponseInput.Row row = inputRows.get(index);
            String prefix = "Row " + rowNumber + ": ";
            if (row == null) {
                errors.add(prefix + "row is empty.");
                continue;
            }
            Integer serialNumber = serialNumber(row.serialNumber(), batchItems.size(), prefix, errors);
            if (serialNumber != null && !serialNumbers.add(serialNumber))
                errors.add(prefix + "S/N is duplicated.");
            VendorExportItem item = serialNumber == null ? null : batchItems.get(serialNumber - 1);
            if (item != null) validateSnapshot(row, item, prefix, errors);
            String type = trim(row.chequeType());
            try { ChequeType.fromCode(type); }
            catch (IllegalArgumentException exception) { errors.add(prefix + "cheque type must be 01, 02 or 03."); }
            int leaves = row.numberOfLeaves() == null ? 0 : row.numberOfLeaves();
            String start = trim(row.rangeStart());
            String end = trim(row.rangeStop());
            BigInteger startNumber = number(start, prefix + "Range Start", errors);
            BigInteger endNumber = number(end, prefix + "Range Stop", errors);
            if (startNumber != null && endNumber != null) {
                if (endNumber.compareTo(startNumber) < 0) errors.add(prefix + "Range Stop is before Range Start.");
                else if (!endNumber.subtract(startNumber).add(BigInteger.ONE).equals(BigInteger.valueOf(leaves)))
                    errors.add(prefix + "serial range does not equal the leaf count.");
            }
            String reference = serialNumber == null ? "" : batchNumber + "-SN-" + serialNumber;
            if (item != null && startNumber != null && endNumber != null)
                result.add(new ValidatedRow(item, type, start, end, startNumber, endNumber, leaves, reference));
        }
        if (serialNumbers.size() != batchItems.size()
                || !serialNumbers.containsAll(java.util.stream.IntStream.rangeClosed(1, batchItems.size()).boxed().toList()))
            errors.add("The response must contain exactly one row for every request in the batch.");
        if (!errors.isEmpty()) throw invalidRows(errors);
        return result;
    }

    private void validateSnapshot(VendorPrintResponseInput.Row row, VendorExportItem item, String prefix,
            List<String> errors) {
        matches(row.accountNumber(), item.getAccountNumber(), "Account Number", prefix, errors);
        matches(row.accountName(), item.getAccountName(), "Account Name", prefix, errors);
        matches(row.branchName(), item.getBranchName(), "Branch Name", prefix, errors);
        matches(row.branchAddress(), item.getBranchAddress(), "Branch Address", prefix, errors);
        matches(row.sortCode(), item.getSortCode(), "Sort Code", prefix, errors);
        matches(row.chequeType(), item.getChequeTypeCode(), "Cheque Type", prefix, errors);
        if (row.numberOfLeaves() == null || row.numberOfLeaves() != item.getLeafCount())
            errors.add(prefix + "No of Leaves does not match the exported request.");
        if (row.numberOfBooklets() == null || row.numberOfBooklets() != item.getBookletCount())
            errors.add(prefix + "No of Booklets does not match the exported request.");
        matches(row.deliverySolId(), item.getDeliverySolId(), "Delivery SolID", prefix, errors);
        matches(row.deliveryAddress(), item.getDeliveryAddress(), "Delivery Address", prefix, errors);
        matches(row.schemeCode(), item.getSchemeCode(), "Scheme Code", prefix, errors);
        matches(row.currency(), item.getCurrency(), "Currency", prefix, errors);
    }

    private void matches(String actual, String expected, String label, String prefix, List<String> errors) {
        if (!trim(actual).equals(trim(expected)))
            errors.add(prefix + label + " does not match the exported request.");
    }

    private Integer serialNumber(String value, int batchSize, String prefix, List<String> errors) {
        String normalized = trim(value);
        if (!normalized.matches("\\d+")) {
            errors.add(prefix + "S/N must be a whole number.");
            return null;
        }
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < 1 || parsed > batchSize) {
                errors.add(prefix + "S/N does not belong to this batch.");
                return null;
            }
            return parsed;
        }
        catch (NumberFormatException exception) {
            errors.add(prefix + "S/N is outside the supported range.");
            return null;
        }
    }

    private void validateNoOverlaps(List<ValidatedRow> rows) {
        Map<String, List<ValidatedRow>> byType = rows.stream().collect(Collectors.groupingBy(ValidatedRow::chequeType));
        for (var entry : byType.entrySet()) {
            List<ValidatedRow> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(ValidatedRow::startNumber)).toList();
            for (int index = 1; index < sorted.size(); index++) {
                if (sorted.get(index - 1).endNumber().compareTo(sorted.get(index).startNumber()) >= 0)
                    throw new ApiException(HttpStatus.CONFLICT, "CHEQUE_SERIAL_RANGE_OVERLAP",
                            "Vendor response contains overlapping ranges for cheque type " + entry.getKey() + ".");
            }
            for (ChequeSerialAllocation existing : allocations.findByChequeTypeCode(entry.getKey())) {
                BigInteger existingStart = new BigInteger(existing.getStartChequeNumber());
                BigInteger existingEnd = new BigInteger(existing.getEndChequeNumber());
                for (ValidatedRow row : sorted) {
                    if (existing.getRequest().getRequestNumber().equals(row.item().getRequest().getRequestNumber()))
                        continue;
                    if (existingStart.compareTo(row.endNumber()) <= 0 && existingEnd.compareTo(row.startNumber()) >= 0)
                        throw new ApiException(HttpStatus.CONFLICT, "CHEQUE_SERIAL_RANGE_OVERLAP",
                                "Serial range overlaps an existing cheque type " + entry.getKey() + " allocation.");
                }
            }
        }
    }

    private void lockType(String type) {
        if (typeLocks.findByCodeForUpdate(type).isPresent()) return;
        typeLocks.saveAndFlush(new ChequeTypeSequenceLock(type));
        typeLocks.findByCodeForUpdate(type).orElseThrow();
    }

    private ChequeSerialAllocation allocationForUpdate(String requestNumber) {
        return allocations.findByRequestNumberForUpdate(requestNumber).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "SERIAL_ALLOCATION_NOT_FOUND",
                        "No vendor serial allocation exists for this request."));
    }

    private void transition(ChequeRequest request, RequestStatus status, StaffUser actor, String note) {
        String from = request.getStatus().name();
        request.setStatus(status);
        history.save(new RequestHistory(request, from, status.name(), actor.getFullName(), note));
        audit.record("REQUEST_STATUS_CHANGED", "CHEQUE_REQUEST", request.getRequestNumber(), from + " -> " + status);
        email.requestTransitioned(request, status);
    }

    private Map<String, Object> allocationMap(ChequeSerialAllocation allocation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", allocation.getRequest().getRequestNumber());
        result.put("customer", allocation.getRequest().getAccountName());
        result.put("accountNumber", allocation.getRequest().getAccountNumber());
        result.put("chequeTypeCode", allocation.getChequeTypeCode());
        result.put("startChequeNumber", allocation.getStartChequeNumber());
        result.put("endChequeNumber", allocation.getEndChequeNumber());
        result.put("leafCount", allocation.getLeafCount());
        result.put("vendorReference", allocation.getVendorReference());
        result.put("printStatus", allocation.getPrintStatus());
        result.put("finacleRegistrationStatus", allocation.getFinacleRegistrationStatus());
        result.put("finacleHostReference", allocation.getFinacleHostReference());
        result.put("finacleResponseCode", allocation.getFinacleResponseCode());
        result.put("failureReason", allocation.getFailureReason());
        return result;
    }

    private BigInteger number(String value, String label, List<String> errors) {
        if (!value.matches("\\d{1,30}")) {
            errors.add(label + " must contain 1 to 30 digits.");
            return null;
        }
        return new BigInteger(value);
    }

    private ApiException invalidRows(List<String> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VENDOR_PRINT_RESPONSE", String.join(" ", errors));
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }

    public record ImportResult(String batchNumber, int importedRows, List<String> registrations) {}
    public record RegistrationCommand(String requestNumber, String accountNumber, int leafCount, String branchCode,
            String startChequeNumber) {}
    private record ValidatedRow(VendorExportItem item, String chequeType, String start, String end,
            BigInteger startNumber, BigInteger endNumber, int leafCount, String vendorReference) {}
}
