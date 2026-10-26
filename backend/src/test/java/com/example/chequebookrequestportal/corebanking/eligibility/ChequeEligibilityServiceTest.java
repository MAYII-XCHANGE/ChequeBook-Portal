package com.example.chequebookrequestportal.corebanking.eligibility;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

class ChequeEligibilityServiceTest {
    private static final String ACCOUNT = "1003000321";
    private ChequeEligibilityLookup lookup;
    private AuditService audit;
    private WorkflowEmailService email;
    private ChequeEligibilityService service;

    @BeforeEach
    void setUp() {
        lookup = mock(ChequeEligibilityLookup.class);
        audit = mock(AuditService.class);
        email = mock(WorkflowEmailService.class);
        service = new ChequeEligibilityService(lookup, audit, email);
    }

    @Test
    void allowsAnAccountWithYFlag() {
        when(lookup.findChequeAllowedFlag(ACCOUNT)).thenReturn(new ChequeEligibilityLookup.Result(true, "Y"));

        assertDoesNotThrow(() -> service.requireChequeAllowed(ACCOUNT));
    }

    @Test
    void rejectsAnAccountWithNFlag() {
        when(lookup.findChequeAllowedFlag(ACCOUNT)).thenReturn(new ChequeEligibilityLookup.Result(true, "N"));

        ApiException exception = assertThrows(ApiException.class, () -> service.requireChequeAllowed(ACCOUNT));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("ACCOUNT_NOT_CHEQUE_ENABLED", exception.getCode());
    }

    @Test
    void reportsAnAccountThatDoesNotExist() {
        when(lookup.findChequeAllowedFlag(ACCOUNT)).thenReturn(new ChequeEligibilityLookup.Result(false, null));

        ApiException exception = assertThrows(ApiException.class, () -> service.requireChequeAllowed(ACCOUNT));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("ACCOUNT_NOT_FOUND", exception.getCode());
    }

    @Test
    void failsClosedWhenTheDatabaseIsUnavailable() {
        when(lookup.findChequeAllowedFlag(ACCOUNT))
                .thenThrow(new DataAccessResourceFailureException("connection unavailable"));

        ApiException exception = assertThrows(ApiException.class, () -> service.requireChequeAllowed(ACCOUNT));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("CHEQUE_ELIGIBILITY_UNAVAILABLE", exception.getCode());
        assertEquals(true, exception.isRetryable());
    }

    @Test
    void sendsAnUnexpectedFlagForOperationsReview() {
        when(lookup.findChequeAllowedFlag(ACCOUNT)).thenReturn(new ChequeEligibilityLookup.Result(true, null));

        ApiException exception = assertThrows(ApiException.class, () -> service.requireChequeAllowed(ACCOUNT));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("CHEQUE_ELIGIBILITY_REVIEW_REQUIRED", exception.getCode());
        verify(audit).record("CHEQUE_ELIGIBILITY_REVIEW_REQUIRED", "ACCOUNT", "100****321",
                "Finacle returned unexpected chq_alwd_flg value NULL/BLANK.");
        verify(email).chequeEligibilityReviewRequired(ACCOUNT, "NULL/BLANK");
    }
}
