package com.example.chequebookrequestportal.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.request.RequestService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {
    @Mock
    private RequestService requests;
    @Mock
    private AuditService audit;

    @Test
    void csvExportsFullAccountNumberAndOperationalDetails() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "CBR-2026-000001");
        row.put("customer", "Customer Name");
        row.put("accountNumber", "0123456789");
        row.put("acctOwnership", "SINGLE");
        row.put("chequeType", "01 — Individual Current Account");
        row.put("accountHomeBranch", "Ikeja (003)");
        row.put("accountHomeBranchCode", "003");
        row.put("servicingBranch", "Victoria Island");
        row.put("servicingBranchCode", "001");
        row.put("createdBy", "CSO User");
        row.put("leafCount", 50);
        row.put("bookletCount", 1);
        row.put("createdAt", Instant.parse("2026-10-21T10:00:00Z"));
        row.put("status", "APPROVED");
        row.put("debitStatus", "SUCCESSFUL");
        row.put("chargeAmount", "1000.00");
        row.put("vatAmount", "75.00");
        row.put("totalDebit", "1075.00");
        row.put("currency", "NGN");
        org.mockito.Mockito.when(requests.listForReportExport("001", "2026-10-01", 0, 100))
                .thenReturn(Map.of("data", List.of(row), "total", 1L));
        ReportController controller = new ReportController(requests, audit);

        ResponseEntity<byte[]> response = controller.csv("001", "2026-10-01", "2026-10-31");
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertThat(csv).contains("Account Ownership", "Cheque Type", "Initiated By", "Total Debit");
        assertThat(csv).doesNotContain("Starting Cheque Number", "Ending Cheque Number",
                "Finacle Registration Status");
        assertThat(csv).contains("\"0123456789\"");
        assertThat(csv).doesNotContain("****", "••••");
        assertThat(csv).contains("\"01 — Individual Current Account\"", "\"CSO User\"", "\"1075.00\"", "\"NGN\"");
        verify(audit).record("REQUEST_REPORT_EXPORTED", "CHEQUE_REQUEST_REPORT", "001",
                "Cheque request CSV exported for branch/SOL 001 with 1 record(s), from 2026-10-01 to 2026-10-31.");
    }
}
