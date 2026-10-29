package com.example.chequebookrequestportal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.RequestHistory;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.domain.VendorExportBatch;
import com.example.chequebookrequestportal.domain.VendorExportItem;
import com.example.chequebookrequestportal.repository.ChargeTransactionRepository;
import com.example.chequebookrequestportal.repository.ChequeRequestRepository;
import com.example.chequebookrequestportal.repository.RequestHistoryRepository;
import com.example.chequebookrequestportal.repository.VendorExportBatchRepository;
import com.example.chequebookrequestportal.repository.VendorExportItemRepository;
import com.example.chequebookrequestportal.security.SecurityService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class VendorExportServiceTest {
    private static final String BATCH_NUMBER = "VEX-20261015-1";

    private VendorExportBatchRepository batches;
    private VendorExportItemRepository items;
    private ChequeRequestRepository requests;
    private ChargeTransactionRepository charges;
    private RequestHistoryRepository history;
    private SecurityService security;
    private AuditService audit;
    private VendorExportService service;
    private VendorWorkflowProperties workflow;
    private StaffUser actor;
    private VendorExportBatch batch;
    private ChequeRequest first;
    private ChequeRequest second;

    @BeforeEach
    void setUp() {
        batches = mock(VendorExportBatchRepository.class);
        items = mock(VendorExportItemRepository.class);
        requests = mock(ChequeRequestRepository.class);
        charges = mock(ChargeTransactionRepository.class);
        history = mock(RequestHistoryRepository.class);
        security = mock(SecurityService.class);
        audit = mock(AuditService.class);
        workflow = mock(VendorWorkflowProperties.class);
        actor = new StaffUser("OPS-1", "operations@novabank.ng", "Operations User", null);

        batch = new VendorExportBatch(2, actor, ".pending-test.tmp");
        ReflectionTestUtils.setField(batch, "id", 1L);
        ReflectionTestUtils.setField(batch, "createdAt", Instant.parse("2026-10-15T08:00:00Z"));
        batch.assignIdentity(BATCH_NUMBER);
        batch.confirmSent(actor);

        first = request(11L, "CBR-001", "1234567890");
        second = request(12L, "CBR-002", "1234567891");
        VendorExportItem firstItem = new VendorExportItem(batch, first, "01", "001", "Head Office", "SAV");
        VendorExportItem secondItem = new VendorExportItem(batch, second, "01", "001", "Head Office", "SAV");

        when(batches.findByBatchNumberForUpdate(BATCH_NUMBER)).thenReturn(Optional.of(batch));
        when(batches.findByBatchNumber(BATCH_NUMBER)).thenReturn(Optional.of(batch));
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH_NUMBER)).thenReturn(List.of(firstItem, secondItem));
        when(requests.findByRequestNumberForUpdate("CBR-001")).thenReturn(Optional.of(first));
        when(requests.findByRequestNumberForUpdate("CBR-002")).thenReturn(Optional.of(second));
        when(security.user()).thenReturn(actor);

        service = new VendorExportService(
                batches,
                items,
                requests,
                charges,
                history,
                security,
                audit,
                "build/test-vendor-exports",
                "001",
                "Head Office",
                "",
                workflow);
    }

    @Test
    void confirmsPrintingForTheCompleteBatchWhenFinacleRegistrationIsDisabled() {
        service.confirmPrinted(BATCH_NUMBER);

        assertEquals(VendorExportBatch.STATUS_PRINTED, batch.getStatus());
        assertEquals(RequestStatus.PRINTED, first.getStatus());
        assertEquals(RequestStatus.PRINTED, second.getStatus());
        verify(history, times(2)).save(any(RequestHistory.class));
        verify(audit).record("VENDOR_BATCH_PRINTED", "VENDOR_EXPORT", BATCH_NUMBER,
                "Marked 2 request(s) as printed.");
    }

    @Test
    void requiresTheVendorResponseWhenFinacleRegistrationIsEnabled() {
        when(workflow.finacleRegistrationEnabled()).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> service.confirmPrinted(BATCH_NUMBER));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("VENDOR_PRINT_RESPONSE_REQUIRED", exception.getCode());
        assertEquals(VendorExportBatch.STATUS_SENT, batch.getStatus());
        assertEquals(RequestStatus.SENT_TO_VENDOR, first.getStatus());
        verify(history, never()).save(any());
    }

    @Test
    void validatesEveryRequestBeforeChangingAnyRequest() {
        second.setStatus(RequestStatus.PRINTED);

        ApiException exception = assertThrows(ApiException.class, () -> service.confirmPrinted(BATCH_NUMBER));

        assertEquals("INVALID_STATUS_TRANSITION", exception.getCode());
        assertEquals(VendorExportBatch.STATUS_SENT, batch.getStatus());
        assertEquals(RequestStatus.SENT_TO_VENDOR, first.getStatus());
        verify(history, never()).save(any());
    }

    @Test
    void rejectsABatchThatHasNotBeenSent() {
        ReflectionTestUtils.setField(batch, "status", VendorExportBatch.STATUS_EXPORTED);

        ApiException exception = assertThrows(ApiException.class, () -> service.confirmPrinted(BATCH_NUMBER));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("INVALID_BATCH_STATUS", exception.getCode());
        assertEquals(RequestStatus.SENT_TO_VENDOR, first.getStatus());
        verify(history, never()).save(any());
    }

    @Test
    void requiresBothExportAndProcessingPermissions() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized."))
                .when(security).requirePermission(Permissions.PROCESS_REQUEST);

        ApiException exception = assertThrows(ApiException.class, () -> service.confirmPrinted(BATCH_NUMBER));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(security).requirePermission(Permissions.EXPORT_VENDOR);
        verify(security).requirePermission(Permissions.PROCESS_REQUEST);
        verify(batches, never()).findByBatchNumberForUpdate(any());
    }

    @Test
    void usesTheChequeTypeStoredOnTheRequest() {
        ChequeRequest corporate = request(13L, "CBR-003", "1234567892", ChequeType.CORPORATE);

        assertEquals("02", service.requireChequeType(corporate));
    }

    @Test
    void allowsManagersChequeToReachVendorExportWithoutADebitTransaction() {
        ChequeRequest managersCheque = request(13L, "CBR-003", "1234567892", ChequeType.MANAGERS_CHEQUE);

        assertEquals("03", service.requireChequeType(managersCheque));
        org.junit.jupiter.api.Assertions.assertTrue(service.financiallyReady(managersCheque));
        verify(charges, never()).existsByRequestRequestNumberAndStatus(any(), any(DebitStatus.class));
    }

    @Test
    void blocksLegacyRequestsWithoutAChequeType() {
        ApiException exception = assertThrows(ApiException.class, () -> service.requireChequeType(first));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("CHEQUE_TYPE_REQUIRED", exception.getCode());
    }

    @Test
    void omitsRangeColumnsFromVendorWorkbooksInManualMode() {
        assertArrayEquals(new String[] { "S/N", "Account Number", "Account Name", "Branch Name",
                "Branch Address", "Sort Code", "Cheque Type", "No of Leaves", "No of Booklets",
                "Delivery SolID", "Delivery Address", "Scheme Code", "Currency" }, service.workbookHeaders());
    }

    @Test
    void restoresRangeColumnsWhenFinacleRegistrationIsEnabled() {
        when(workflow.finacleRegistrationEnabled()).thenReturn(true);

        String[] headers = service.workbookHeaders();

        assertEquals("Range Start", headers[headers.length - 2]);
        assertEquals("Range Stop", headers[headers.length - 1]);
    }

    private ChequeRequest request(long id, String requestNumber, String accountNumber) {
        ChequeRequest request = new ChequeRequest(accountNumber, "Customer", "SINGLE", "SAV", "NGN",
                "001", "Head Office", "1 Bank Street", "001", 50, actor);
        ReflectionTestUtils.setField(request, "id", id);
        request.assignRequestNumber(requestNumber);
        request.setStatus(RequestStatus.SENT_TO_VENDOR);
        return request;
    }

    private ChequeRequest request(long id, String requestNumber, String accountNumber, ChequeType chequeType) {
        ChequeRequest request = new ChequeRequest(accountNumber, "Customer", "CORPORATE", "SAV", chequeType,
                "NGN", null, null, "001", "Head Office", "1 Bank Street", "001", 50, actor);
        ReflectionTestUtils.setField(request, "id", id);
        request.assignRequestNumber(requestNumber);
        request.setStatus(RequestStatus.SENT_TO_VENDOR);
        return request;
    }
}
