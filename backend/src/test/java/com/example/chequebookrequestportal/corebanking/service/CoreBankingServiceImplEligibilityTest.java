package com.example.chequebookrequestportal.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.client.CoreBankingClient;
import com.example.chequebookrequestportal.corebanking.eligibility.ChequeEligibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CoreBankingServiceImplEligibilityTest {
    @Test
    void stopsBeforeNameEnquiryWhenChequeEligibilityFails() {
        CoreBankingClient client = mock(CoreBankingClient.class);
        ChequeEligibilityService eligibility = mock(ChequeEligibilityService.class);
        CoreBankingServiceImpl service = new CoreBankingServiceImpl(client, eligibility, "NGN", "CI", "NIP", "AMT");
        ApiException rejection = new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "ACCOUNT_NOT_CHEQUE_ENABLED", "This account is not enabled for cheque-book issuance.");
        doThrow(rejection).when(eligibility).requireChequeAllowed("1003000321");

        ApiException actual = assertThrows(ApiException.class, () -> service.nameEnquiry("1003000321"));

        assertSame(rejection, actual);
        verify(eligibility).requireChequeAllowed("1003000321");
        verifyNoInteractions(client);
    }
}
