package com.example.chequebookrequestportal.corebanking.managerscheque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup.AccountDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ManagersChequeAccountServiceTest {
    private static final String ACCOUNT = "1003000321";
    private ManagersChequeAccountLookup lookup;
    private ManagersChequeAccountService service;

    @BeforeEach
    void setUp() {
        lookup = mock(ManagersChequeAccountLookup.class);
        service = new ManagersChequeAccountService(lookup);
    }

    @Test
    void returnsTheBranchManagementAccountWithDatabaseProductDetails() {
        when(lookup.findConfiguredAccount(ACCOUNT)).thenReturn(List.of(
                new AccountDetails(" 1003000321 ", " Manager Cheque ", " O ", " 2004 ", " NGN ")));

        AccountDetails result = service.resolve(ACCOUNT, "001");

        assertEquals(new AccountDetails(ACCOUNT, "Manager Cheque", "O", "2004", "NGN"), result);
    }

    @Test
    void rejectsWhenTheBranchManagementAccountIsNotFound() {
        when(lookup.findConfiguredAccount(ACCOUNT)).thenReturn(List.of());

        ApiException exception = assertThrows(ApiException.class, () -> service.resolve(ACCOUNT, "001"));

        assertEquals("MANAGERS_CHEQUE_ACCOUNT_NOT_FOUND", exception.getCode());
    }

    @Test
    void rejectsReturnedAccountDetailsThatDoNotMatchTheConfiguration() {
        when(lookup.findConfiguredAccount(ACCOUNT)).thenReturn(List.of(
                new AccountDetails("1111111111", "Manager Cheque", "O", "2004", "NGN")));

        ApiException exception = assertThrows(ApiException.class, () -> service.resolve(ACCOUNT, "001"));

        assertEquals("MANAGERS_CHEQUE_ACCOUNT_DATA_INVALID", exception.getCode());
    }

    @Test
    void reportsFinacleDatabaseFailuresAsRetryable() {
        when(lookup.findConfiguredAccount(ACCOUNT))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        ApiException exception = assertThrows(ApiException.class, () -> service.resolve(ACCOUNT, "001"));

        assertEquals("MANAGERS_CHEQUE_ENQUIRY_UNAVAILABLE", exception.getCode());
        assertTrue(exception.isRetryable());
    }
}
