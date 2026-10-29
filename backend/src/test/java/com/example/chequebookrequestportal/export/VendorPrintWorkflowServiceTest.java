package com.example.chequebookrequestportal.export;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.request.ChequeBookResponse;
import com.example.chequebookrequestportal.security.SecurityService;
import com.example.chequebookrequestportal.service.ChequeBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class VendorPrintWorkflowServiceTest {
    private VendorPrintPersistenceService persistence;
    private ChequeBookService chequeBooks;
    private VendorPrintWorkflowService service;
    private VendorWorkflowProperties workflow;

    @BeforeEach
    void setUp() {
        persistence = Mockito.mock(VendorPrintPersistenceService.class);
        chequeBooks = Mockito.mock(ChequeBookService.class);
        workflow = Mockito.mock(VendorWorkflowProperties.class);
        when(workflow.finacleRegistrationEnabled()).thenReturn(true);
        service = new VendorPrintWorkflowService(persistence, chequeBooks, Mockito.mock(SecurityService.class),
                workflow);
    }

    @Test
    void blocksFinacleRegistrationActionsWhenTheWorkflowIsDisabled() {
        when(workflow.finacleRegistrationEnabled()).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> service.retry("CBR-001"));

        assertEquals("FINACLE_REGISTRATION_DISABLED", exception.getCode());
        verify(persistence, never()).beginRegistration(Mockito.any(), Mockito.anyBoolean());
        verify(chequeBooks, never()).requestChequeBook(Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void sendsTheVendorProvidedStartingNumberToFinacle() {
        var command = new VendorPrintPersistenceService.RegistrationCommand(
                "CBR-001", "0123456789", 50, "001", "001001");
        when(persistence.beginRegistration("CBR-001", true)).thenReturn(command);
        ChequeBookResponse response = successfulResponse();
        when(chequeBooks.requestChequeBook("0123456789", 50, "001", "001001")).thenReturn(response);

        service.retry("CBR-001");

        verify(chequeBooks).requestChequeBook("0123456789", 50, "001", "001001");
        verify(persistence).completeRegistration("CBR-001", response);
        verify(persistence, never()).failRegistration(Mockito.any(), Mockito.any());
    }

    @Test
    void anUncertainCallIsNotRetriedAndRequiresReview() {
        var command = new VendorPrintPersistenceService.RegistrationCommand(
                "CBR-001", "0123456789", 50, "001", "001001");
        when(persistence.beginRegistration("CBR-001", true)).thenReturn(command);
        when(chequeBooks.requestChequeBook("0123456789", 50, "001", "001001"))
                .thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "CHEQUEBOOK_TIMEOUT", "Timed out", true));

        service.retry("CBR-001");

        verify(chequeBooks).requestChequeBook("0123456789", 50, "001", "001001");
        verify(persistence).requireRegistrationReview("CBR-001", "CHEQUEBOOK_TIMEOUT", "Timed out");
        verify(persistence, never()).completeRegistration(Mockito.any(), Mockito.any());
    }

    @Test
    void aConfirmedHostRejectionBecomesRetryableFailure() {
        var command = new VendorPrintPersistenceService.RegistrationCommand(
                "CBR-001", "0123456789", 50, "001", "001001");
        when(persistence.beginRegistration("CBR-001", true)).thenReturn(command);
        ChequeBookResponse response = new ChequeBookResponse(false, "Rejected", new ChequeBookResponse.Data(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, "HOST-2", "FAILED", null, null, "E01", "Rejected"));
        when(chequeBooks.requestChequeBook("0123456789", 50, "001", "001001")).thenReturn(response);

        service.retry("CBR-001");

        verify(persistence).failRegistration("CBR-001", response);
        verify(persistence, never()).requireRegistrationReview(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private ChequeBookResponse successfulResponse() {
        return new ChequeBookResponse(true, "Successful", new ChequeBookResponse.Data(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, "HOST-1", "SUCCESS", null, null, null, null));
    }
}
