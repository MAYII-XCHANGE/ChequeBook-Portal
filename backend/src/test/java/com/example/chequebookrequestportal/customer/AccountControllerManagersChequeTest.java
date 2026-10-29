package com.example.chequebookrequestportal.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup.AccountDetails;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountService;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.security.SecurityService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountControllerManagersChequeTest {
    private static final String ACCOUNT = "0123456789";
    private CoreBankingService coreBanking;
    private SecurityService security;
    private ManagersChequeAccountService managersChequeAccounts;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        coreBanking = mock(CoreBankingService.class);
        security = mock(SecurityService.class);
        managersChequeAccounts = mock(ManagersChequeAccountService.class);
        Branch branch = new Branch("001", "Head Office", "1 Bank Street", "001", "CHARGE", "VAT", ACCOUNT,
                false);
        when(security.user()).thenReturn(new StaffUser("CSO-1", "cso@bank.test", "Branch CSO", branch));
        when(managersChequeAccounts.resolve(ACCOUNT, "001")).thenReturn(
                new AccountDetails(ACCOUNT, "Manager's Cheque", "O", "2004", "NGN"));
        controller = new AccountController(coreBanking, security, managersChequeAccounts);
    }

    @Test
    void returnsTheConfiguredBranchAccountWithoutRunningCustomerEligibility() {
        Map<String, Object> response = controller.managersChequeAccount();
        Map<?, ?> customer = (Map<?, ?>) response.get("customer");

        assertEquals(ACCOUNT, customer.get("accountNumber"));
        assertEquals("2004", customer.get("schemeCode"));
        assertEquals("NGN", customer.get("currency"));
        verify(managersChequeAccounts).resolve(ACCOUNT, "001");
        verify(coreBanking, never()).nameEnquiry(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void regularChequeRetainsRetailKycChecks() {
        when(coreBanking.nameEnquiry(ACCOUNT)).thenReturn(account("001", "1", "RESTRICTED"));

        Map<String, Object> response = controller.eligibility(ACCOUNT);
        Map<?, ?> eligibility = (Map<?, ?>) response.get("eligibility");

        assertFalse((Boolean) eligibility.get("eligible"));
    }

    @Test
    void rejectsABranchWithoutAConfiguredAccountBeforeCallingFinacle() {
        Branch branch = new Branch("002", "Ikeja", "2 Bank Street", "002", "CHARGE-2", "VAT-2", false);
        when(security.user()).thenReturn(new StaffUser("CSO-2", "cso2@bank.test", "Ikeja CSO", branch));

        ApiException exception = assertThrows(ApiException.class,
                () -> controller.managersChequeAccount());

        assertEquals("MANAGERS_CHEQUE_ACCOUNT_NOT_CONFIGURED", exception.getCode());
        verifyNoInteractions(managersChequeAccounts);
        verify(coreBanking, never()).nameEnquiry(org.mockito.ArgumentMatchers.any());
    }

    private NameEnquiryResponse account(String branchCode, String kycLevel, String restrictions) {
        return new NameEnquiryResponse(ACCOUNT, "Head Office Manager's Cheque", "0", "BANK", "ACTIVE", "NGN",
                "MGR", branchCode, "Head Office", "1 Bank Street", "001", kycLevel, restrictions,
                new NameEnquiryResponse.Status("SUCC", "00", "Successful"));
    }
}
