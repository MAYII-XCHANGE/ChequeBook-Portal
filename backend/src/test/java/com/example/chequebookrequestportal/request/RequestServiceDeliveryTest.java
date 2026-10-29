package com.example.chequebookrequestportal.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountService;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.RequestHistory;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
import com.example.chequebookrequestportal.export.VendorWorkflowProperties;
import com.example.chequebookrequestportal.repository.BranchRepository;
import com.example.chequebookrequestportal.repository.ChargeTransactionRepository;
import com.example.chequebookrequestportal.repository.ChequeNumberRepository;
import com.example.chequebookrequestportal.repository.ChequeRequestRepository;
import com.example.chequebookrequestportal.repository.EligibilityRepository;
import com.example.chequebookrequestportal.repository.RequestHistoryRepository;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import com.example.chequebookrequestportal.security.SecurityService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class RequestServiceDeliveryTest {
    private static final String REQUEST_NUMBER = "CB-20261014-001-1";

    private ChequeRequestRepository requests;
    private RequestHistoryRepository history;
    private ChargeTransactionRepository charges;
    private SecurityService security;
    private AuditService audit;
    private WorkflowEmailService email;
    private RequestService service;
    private VendorWorkflowProperties vendorWorkflow;
    private ChequeRequest request;

    @BeforeEach
    void setUp() {
        requests = mock(ChequeRequestRepository.class);
        history = mock(RequestHistoryRepository.class);
        charges = mock(ChargeTransactionRepository.class);
        security = mock(SecurityService.class);
        audit = mock(AuditService.class);
        email = mock(WorkflowEmailService.class);
        vendorWorkflow = mock(VendorWorkflowProperties.class);

        Branch branch = new Branch("001", "Head Office", "1 Bank Street", "001", "100", "200");
        StaffUser actor = new StaffUser("E001", "cso@novabank.ng", "Branch CSO", branch);
        request = new ChequeRequest("1234564821", "Test Customer", "SOLE", "CA", "NGN",
                "001", "Head Office", "1 Bank Street", "001", 50, actor);
        ReflectionTestUtils.setField(request, "id", 10L);
        request.assignRequestNumber(REQUEST_NUMBER);
        request.setStatus(RequestStatus.RECEIVED_BY_BRANCH);

        when(requests.findByRequestNumberForUpdate(REQUEST_NUMBER)).thenReturn(Optional.of(request));
        when(requests.findByRequestNumber(REQUEST_NUMBER)).thenReturn(Optional.of(request));
        when(security.principal()).thenReturn(new PortalPrincipal(
                1L, "cso", "cso@novabank.ng", "Branch CSO", "CSO", Set.of("CSO"), Set.of(), "001",
                "Head Office"));
        when(security.user()).thenReturn(actor);
        when(charges.findByRequestRequestNumber(REQUEST_NUMBER)).thenReturn(Optional.empty());
        when(history.findByRequestRequestNumberOrderByCreatedAtAsc(REQUEST_NUMBER)).thenReturn(List.of());

        service = new RequestService(
                requests,
                mock(EligibilityRepository.class),
                history,
                charges,
                mock(BranchRepository.class),
                mock(ChequeNumberRepository.class),
                mock(CoreBankingService.class),
                security,
                audit,
                email,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200),
                "9999999999",
                BigDecimal.valueOf(0.075),
                "NGN",
                mock(ManagersChequeAccountService.class),
                vendorWorkflow);
    }

    @Test
    void deliversOnlyAfterAllConfirmationDetailsMatch() {
        service.deliver(REQUEST_NUMBER, confirmation("4821", true, true, 0L));

        assertEquals(RequestStatus.DELIVERED, request.getStatus());
        ArgumentCaptor<RequestHistory> savedHistory = ArgumentCaptor.forClass(RequestHistory.class);
        verify(history).save(savedHistory.capture());
        assertEquals("RECEIVED_BY_BRANCH", savedHistory.getValue().getFromStatus());
        assertEquals("DELIVERED", savedHistory.getValue().getToStatus());
        assertEquals("Customer identity and physical cheque-book handover confirmed by branch CSO.",
                savedHistory.getValue().getNote());
        verify(audit).record("REQUEST_STATUS_CHANGED", "CHEQUE_REQUEST", REQUEST_NUMBER,
                "RECEIVED_BY_BRANCH -> DELIVERED");
        verify(email).requestTransitioned(request, RequestStatus.DELIVERED);
    }

    @Test
    void incorrectAccountSuffixDoesNotChangeOrRecordAnything() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.deliver(REQUEST_NUMBER, confirmation("9999", true, true, 0L)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("ACCOUNT_SUFFIX_MISMATCH", exception.getCode());
        assertEquals(RequestStatus.RECEIVED_BY_BRANCH, request.getStatus());
        verify(history, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any());
        verify(email, never()).requestTransitioned(any(), any());
    }

    @Test
    void staleRequestVersionIsRejectedBeforeDelivery() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.deliver(REQUEST_NUMBER, confirmation("4821", true, true, 1L)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STALE_REQUEST_VERSION", exception.getCode());
        assertEquals(RequestStatus.RECEIVED_BY_BRANCH, request.getStatus());
        verify(history, never()).save(any());
    }

    @Test
    void uncheckedAttestationIsRejectedByTheService() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.deliver(REQUEST_NUMBER, confirmation("4821", false, true, 0L)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("DELIVERY_CONFIRMATION_REQUIRED", exception.getCode());
        assertEquals(RequestStatus.RECEIVED_BY_BRANCH, request.getStatus());
    }

    @Test
    void repeatedSubmissionCannotDeliverTwice() {
        DeliveryConfirmationRequest confirmation = confirmation("4821", true, true, 0L);
        service.deliver(REQUEST_NUMBER, confirmation);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deliver(REQUEST_NUMBER, confirmation));

        assertEquals("INVALID_STATUS_TRANSITION", exception.getCode());
        verify(history).save(any(RequestHistory.class));
        verify(email).requestTransitioned(request, RequestStatus.DELIVERED);
    }

    @Test
    void anotherBranchCannotConfirmDelivery() {
        when(security.principal()).thenReturn(new PortalPrincipal(
                2L, "other", "other@novabank.ng", "Other CSO", "CSO", Set.of("CSO"), Set.of(), "002",
                "Other Branch"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deliver(REQUEST_NUMBER, confirmation("4821", true, true, 0L)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("BRANCH_SCOPE_VIOLATION", exception.getCode());
        assertEquals(RequestStatus.RECEIVED_BY_BRANCH, request.getStatus());
    }

    @Test
    void individualPrintingIsRejectedWithoutChangingTheRequest() {
        request.setStatus(RequestStatus.SENT_TO_VENDOR);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.rejectIndividualPrinting(REQUEST_NUMBER));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("BATCH_PRINT_CONFIRMATION_REQUIRED", exception.getCode());
        assertEquals(RequestStatus.SENT_TO_VENDOR, request.getStatus());
        verify(history, never()).save(any());
    }

    @Test
    void processingQueueIncludesPrintedRequestsInManualMode() {
        service.processingQueue();

        verify(requests).findProcessingQueue(
                argThat(statuses -> !statuses.contains(RequestStatus.SENT_TO_VENDOR)
                        && statuses.contains(RequestStatus.PRINTED)
                        && statuses.contains(RequestStatus.FINACLE_REGISTERED)),
                eq(DebitStatus.SUCCESSFUL));
    }

    @Test
    void aPrintedRequestCanBeDispatchedInManualMode() {
        request.setStatus(RequestStatus.PRINTED);
        when(charges.existsByRequestRequestNumberAndStatus(REQUEST_NUMBER, DebitStatus.SUCCESSFUL)).thenReturn(true);

        service.dispatch(REQUEST_NUMBER);

        assertEquals(RequestStatus.DISPATCHED, request.getStatus());
    }

    @Test
    void aFinacleRegisteredRequestCanBeDispatchedWhenFinacleRegistrationIsEnabled() {
        when(vendorWorkflow.finacleRegistrationEnabled()).thenReturn(true);
        request.setStatus(RequestStatus.FINACLE_REGISTERED);
        when(charges.existsByRequestRequestNumberAndStatus(REQUEST_NUMBER, DebitStatus.SUCCESSFUL)).thenReturn(true);

        service.dispatch(REQUEST_NUMBER);

        assertEquals(RequestStatus.DISPATCHED, request.getStatus());
    }

    @Test
    void anAlreadyRegisteredRequestRemainsDispatchableAfterSwitchingToManualMode() {
        request.setStatus(RequestStatus.FINACLE_REGISTERED);
        when(charges.existsByRequestRequestNumberAndStatus(REQUEST_NUMBER, DebitStatus.SUCCESSFUL)).thenReturn(true);

        service.dispatch(REQUEST_NUMBER);

        assertEquals(RequestStatus.DISPATCHED, request.getStatus());
    }

    private DeliveryConfirmationRequest confirmation(String lastFour, boolean identityVerified,
            boolean handoverConfirmed, long version) {
        return new DeliveryConfirmationRequest(lastFour, identityVerified, handoverConfirmed, version);
    }
}
