package com.example.chequebookrequestportal.report;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.request.RequestService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/requests")
@PreAuthorize("hasAuthority('VIEW_REPORTS')")
public class ReportController {
    private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy",
            Locale.forLanguageTag("en-NG")).withZone(ZoneId.of("Africa/Lagos"));
    private final RequestService requests;
    private final AuditService audit;

    public ReportController(RequestService requests, AuditService audit) {
        this.requests = requests;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> report(@RequestParam(defaultValue = "") String branch,
                                      @RequestParam(defaultValue = "") String from,
                                      @RequestParam(defaultValue = "") String to) {
        LocalDate end = parseDate(to, "to");
        parseDate(from, "from");
        List<Map<String, Object>> rows = rows(branch, from, end, false);
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) rows.size());
        summary.put("approved", count(rows, "APPROVED"));
        summary.put("rejected", count(rows, "REJECTED"));
        summary.put("processing", rows.stream().filter(row -> List.of("DEBIT_PENDING", "EXPORTED", "SENT_TO_VENDOR",
            "PRINTED", "FINACLE_REGISTRATION_PENDING", "FINACLE_REGISTERED", "FINACLE_REGISTRATION_FAILED",
            "FINACLE_REGISTRATION_REVIEW_REQUIRED", "DISPATCHED", "RECEIVED_BY_BRANCH")
                .contains(String.valueOf(row.get("status")))).count());
        summary.put("delivered", count(rows, "DELIVERED"));
        return Map.of("summary", summary, "data", rows);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> csv(@RequestParam(defaultValue = "") String branch,
                                      @RequestParam(defaultValue = "") String from,
                                      @RequestParam(defaultValue = "") String to) {
        LocalDate end = parseDate(to, "to");
        parseDate(from, "from");
        List<Map<String, Object>> rows = rows(branch, from, end, true);
        boolean includeFinacleRegistration = requests.finacleRegistrationEnabled();
        StringBuilder csv = new StringBuilder(
                "Request ID,Customer Name,Account Number,Account Ownership,Cheque Type,"
                        + (includeFinacleRegistration
                                ? "Starting Cheque Number,Ending Cheque Number," : "")
                        + "Vendor Reference,"
                        + (includeFinacleRegistration ? "Finacle Registration Status," : "")
                        + "Domicile Branch,Domicile Branch Code,"
                        + "Initiating Branch,Initiating Branch Code,Initiated By,Leaf Count,Booklet Count,"
                        + "Created Date,Status,Debit Status,Charge Amount,VAT Amount,Total Debit,Currency\r\n");
        for (var row : rows) {
            csv.append(q(row.get("id"))).append(',').append(q(row.get("customer"))).append(',')
                .append(q(row.get("accountNumber"))).append(',').append(q(row.get("acctOwnership"))).append(',')
                .append(q(row.get("chequeType"))).append(',');
            if (includeFinacleRegistration)
                csv.append(q(row.get("startChequeNumber"))).append(',')
                        .append(q(row.get("endChequeNumber"))).append(',');
            csv.append(q(row.get("vendorReference"))).append(',');
            if (includeFinacleRegistration)
                csv.append(q(row.get("finacleRegistrationStatus"))).append(',');
            csv.append(q(row.get("accountHomeBranch"))).append(',')
                .append(q(row.get("accountHomeBranchCode"))).append(',')
                .append(q(row.get("servicingBranch"))).append(',')
                .append(q(row.get("servicingBranchCode"))).append(',')
                .append(q(row.get("createdBy"))).append(',').append(q(row.get("leafCount"))).append(',')
                .append(q(row.get("bookletCount"))).append(',').append(q(displayDate(row.get("createdAt"))))
                .append(',').append(q(statusLabel(row.get("status")))).append(',')
                .append(q(financialValue(row.get("debitStatus")))).append(',')
                .append(q(financialValue(row.get("chargeAmount")))).append(',')
                .append(q(financialValue(row.get("vatAmount")))).append(',')
                .append(q(financialValue(row.get("totalDebit")))).append(',')
                .append(q(row.get("currency"))).append("\r\n");
        }
        String reportScope = branch == null || branch.isBlank() ? "AUTHORIZED_BRANCHES" : branch;
        audit.record("REQUEST_REPORT_EXPORTED", "CHEQUE_REQUEST_REPORT", reportScope,
                "Cheque request CSV exported for branch/SOL " + reportScope + " with " + rows.size()
                        + " record(s), from " + valueOrAll(from) + " to " + valueOrAll(to) + ".");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename(branch, from, to) + "\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private List<Map<String, Object>> rows(String branch, String from, LocalDate end,
            boolean includeFullAccountNumber) {
        return allRows(branch, from, includeFullAccountNumber).stream()
                .filter(row -> end == null
                        || !LocalDate.parse(String.valueOf(row.get("createdAt")).substring(0, 10)).isAfter(end))
                .toList();
    }

    private List<Map<String, Object>> allRows(String branch, String from, boolean includeFullAccountNumber) {
        List<Map<String, Object>> result = new ArrayList<>();
        int page = 0;
        long total;
        do {
            Map<String, Object> response = includeFullAccountNumber
                    ? requests.listForReportExport(branch, from, page++, 100)
                    : requests.list("", "", branch, from, "", page++, 100, "newest");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> batch = (List<Map<String, Object>>) response.get("data");
            result.addAll(batch);
            total = ((Number) response.get("total")).longValue();
        } while (result.size() < total);
        return result;
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", field + " must use YYYY-MM-DD format.");
        }
    }

    private long count(List<Map<String, Object>> rows, String status) {
        return rows.stream().filter(row -> status.equals(String.valueOf(row.get("status")))).count();
    }

    private String q(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String trimmed = text.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String valueOrAll(String value) {
        return value == null || value.isBlank() ? "all dates" : value;
    }

    private Object financialValue(Object value) {
        return "NOT_REQUIRED".equals(String.valueOf(value)) ? "Not required" : value;
    }

    private String filename(String branch, String from, String to) {
        StringBuilder name = new StringBuilder("cheque-request-report");
        if (from != null && !from.isBlank()) name.append('_').append(from);
        if (to != null && !to.isBlank()) name.append('_').append(to);
        if (branch != null && !branch.isBlank()) name.append("_branch-").append(branch.replaceAll("[^A-Za-z0-9_-]", ""));
        return name.append(".csv").toString();
    }

    private String displayDate(Object value) {
        if (value == null) return "";
        try {
            return REPORT_DATE.format(value instanceof Instant instant ? instant : Instant.parse(String.valueOf(value)));
        }
        catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private String statusLabel(Object value) {
        String status = value == null ? "" : String.valueOf(value);
        return switch (status) {
            case "PENDING_APPROVAL" -> "Pending approval";
            case "DEBIT_PENDING" -> "Debit pending";
            case "DEBIT_FAILED" -> "Debit failed";
            case "DEBIT_REVIEW_REQUIRED" -> "Debit review";
            case "CHEQUEBOOK_REQUEST_FAILED" -> "Cheque request failed (reversed)";
            case "REVERSAL_REVIEW_REQUIRED" -> "Reversal review required";
            case "APPROVED" -> "Ready for export";
            case "REJECTED" -> "Rejected";
            case "EXPORTED" -> "Exported";
            case "SENT_TO_VENDOR" -> "Sent to vendor";
            case "PRINTED" -> "Printed";
            case "FINACLE_REGISTRATION_PENDING" -> "Finacle registration pending";
            case "FINACLE_REGISTERED" -> "Finacle registered";
            case "FINACLE_REGISTRATION_FAILED" -> "Finacle registration failed";
            case "FINACLE_REGISTRATION_REVIEW_REQUIRED" -> "Finacle registration review required";
            case "DISPATCHED" -> "Dispatched";
            case "RECEIVED_BY_BRANCH" -> "Received by branch";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }
}
