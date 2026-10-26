package com.example.chequebookrequestportal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeSerialAllocation;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.ChequeTypeSequenceLock;
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
import com.example.chequebookrequestportal.security.SecurityService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorPrintPersistenceServiceTest {
    private static final String BATCH = "VEX-1";
    private VendorExportBatchRepository batches;
    private VendorExportItemRepository items;
    private ChequeSerialAllocationRepository allocations;
    private ChequeTypeSequenceLockRepository typeLocks;
    private SecurityService security;
    private VendorPrintPersistenceService service;
    private StaffUser actor;
    private VendorExportBatch batch;

    @BeforeEach
    void setUp() {
        batches = mock(VendorExportBatchRepository.class);
        items = mock(VendorExportItemRepository.class);
        allocations = mock(ChequeSerialAllocationRepository.class);
        typeLocks = mock(ChequeTypeSequenceLockRepository.class);
        security = mock(SecurityService.class);
        actor = new StaffUser("OPS-1", "ops@bank.test", "Operations", null);
        ReflectionTestUtils.setField(actor, "id", 1L);
        batch = new VendorExportBatch(1, actor, ".pending");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.assignIdentity(BATCH);
        batch.confirmSent(actor);
        when(batches.findByBatchNumberForUpdate(BATCH)).thenReturn(Optional.of(batch));
        when(security.user()).thenReturn(actor);
        when(typeLocks.findByCodeForUpdate(any())).thenAnswer(invocation -> Optional.of(
                new ChequeTypeSequenceLock(invocation.getArgument(0))));
        when(allocations.findByRequestNumberForUpdate(any())).thenReturn(Optional.empty());
        when(allocations.findByChequeTypeCode(any())).thenReturn(List.of());
        when(allocations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new VendorPrintPersistenceService(batches, items, allocations, typeLocks,
                mock(RequestHistoryRepository.class), security, mock(AuditService.class),
                mock(WorkflowEmailService.class));
    }

    @Test
    void importsACompleteValidVendorRange() {
        VendorExportItem item = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(item));

        var result = service.importRows(BATCH, List.of(row("CBR-1", "01", "001001", "001050", 50)));

        assertEquals(List.of("CBR-1"), result.registrations());
        assertEquals(RequestStatus.PRINTED, item.getRequest().getStatus());
        assertEquals(VendorExportBatch.STATUS_PRINTED, batch.getStatus());
    }

    @Test
    void rejectsAnIncorrectRangeLengthBeforePersisting() {
        VendorExportItem item = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(item));

        ApiException exception = assertThrows(ApiException.class, () -> service.importRows(BATCH,
                List.of(row("CBR-1", "01", "001001", "001049", 50))));

        assertEquals("INVALID_VENDOR_PRINT_RESPONSE", exception.getCode());
        assertEquals(RequestStatus.SENT_TO_VENDOR, item.getRequest().getStatus());
    }

    @Test
    void rejectsAChangedExportedField() {
        VendorExportItem item = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(item));
        VendorPrintResponseInput.Row valid = row("CBR-1", "01", "001001", "001050", 50);
        VendorPrintResponseInput.Row changedAccount = new VendorPrintResponseInput.Row(valid.serialNumber(),
                "9999999999", valid.accountName(), valid.branchName(), valid.branchAddress(), valid.sortCode(),
                valid.chequeType(), valid.numberOfLeaves(), valid.numberOfBooklets(), valid.deliverySolId(),
                valid.deliveryAddress(), valid.schemeCode(), valid.currency(), valid.rangeStart(), valid.rangeStop());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.importRows(BATCH, List.of(changedAccount)));

        assertEquals("INVALID_VENDOR_PRINT_RESPONSE", exception.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("Account Number"));
        verify(allocations, never()).save(any());
    }

    @Test
    void rejectsUnsupportedManagersChequeType() {
        VendorExportItem item = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(item));

        ApiException exception = assertThrows(ApiException.class, () -> service.importRows(BATCH,
                List.of(row("CBR-1", "03", "001001", "001050", 50))));

        assertEquals("INVALID_VENDOR_PRINT_RESPONSE", exception.getCode());
    }

    @Test
    void permitsTheSameNumericRangeAcrossDifferentChequeTypes() {
        VendorExportItem individual = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        VendorExportItem corporate = item("CBR-2", ChequeType.CORPORATE);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(individual, corporate));

        var result = service.importRows(BATCH, List.of(
                row("CBR-1", "01", "001001", "001050", 50),
                row("CBR-2", "02", "001001", "001050", 50)));

        assertEquals(2, result.importedRows());
    }

    @Test
    void rejectsOverlappingRangesWithinTheSameChequeType() {
        VendorExportItem first = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        VendorExportItem second = item("CBR-2", ChequeType.INDIVIDUAL_CURRENT);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(first, second));

        ApiException exception = assertThrows(ApiException.class, () -> service.importRows(BATCH, List.of(
                row("CBR-1", "01", "001001", "001050", 50),
                row("CBR-2", "01", "001025", "001074", 50))));

        assertEquals("CHEQUE_SERIAL_RANGE_OVERLAP", exception.getCode());
    }

    @Test
    void reimportingTheExactAllocationIsIdempotent() {
        VendorExportItem item = item("CBR-1", ChequeType.INDIVIDUAL_CURRENT);
        ChequeSerialAllocation existing = new ChequeSerialAllocation(item.getRequest(), batch, "01", "001001",
                "001050", 50, BATCH + "-SN-1", actor);
        when(items.findByBatchBatchNumberOrderByIdAsc(BATCH)).thenReturn(List.of(item));
        when(allocations.findByRequestNumberForUpdate("CBR-1")).thenReturn(Optional.of(existing));
        when(allocations.findByChequeTypeCode("01")).thenReturn(List.of(existing));

        var result = service.importRows(BATCH, List.of(row("CBR-1", "01", "001001", "001050", 50)));

        assertEquals(List.of("CBR-1"), result.registrations());
        verify(allocations, never()).save(any());
    }

    private VendorExportItem item(String requestNumber, ChequeType type) {
        ChequeRequest request = new ChequeRequest("0123456789", "Customer", "SINGLE", "CUR", type, "NGN",
                null, null, "001", "Head Office", "1 Bank Street", "001", 50, actor);
        ReflectionTestUtils.setField(request, "id", Math.abs((long) requestNumber.hashCode()));
        request.assignRequestNumber(requestNumber);
        request.setStatus(RequestStatus.SENT_TO_VENDOR);
        return new VendorExportItem(batch, request, type.code(), "001", "Head Office", "CUR");
    }

    private VendorPrintResponseInput.Row row(String requestNumber, String type, String start, String end, int leaves) {
        String serialNumber = requestNumber.substring(requestNumber.lastIndexOf('-') + 1);
        String accountNumber = "0123456789";
        return new VendorPrintResponseInput.Row(serialNumber, accountNumber, "Customer", "Head Office",
                "1 Bank Street", "001", type, leaves, 1, "001", "Head Office", "CUR", "NGN", start, end);
    }
}
