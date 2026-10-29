package com.example.chequebookrequestportal.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup.AccountDetails;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountService;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.ChargeTransaction;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.DebitPolicy;
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
import org.springframework.test.util.ReflectionTestUtils;

class RequestServiceManagersChequeTest {
    private static final String ACCOUNT = "0123456789";
    private ChequeRequestRepository requests;
    private ChargeTransactionRepository charges;
    private BranchRepository branches;
    private CoreBankingService coreBanking;
    private SecurityService security;
    private AuditService audit;
    private RequestHistoryRepository history;
    private WorkflowEmailService email;
    private EligibilityRepository eligibility;
    private ManagersChequeAccountService managersChequeAccounts;
    private Branch branch;
    private StaffUser cso;
    private RequestService service;

    @BeforeEach
    void setUp() {
        requests = mock(ChequeRequestRepository.class);
        charges = mock(ChargeTransactionRepository.class);
        branches = mock(BranchRepository.class);
        coreBanking = mock(CoreBankingService.class);
        security = mock(SecurityService.class);
        audit = mock(AuditService.class);
        history = mock(RequestHistoryRepository.class);
        email = mock(WorkflowEmailService.class);
        eligibility = mock(EligibilityRepository.class);
        managersChequeAccounts = mock(ManagersChequeAccountService.class);
        branch = new Branch("001", "Head Office", "1 Bank Street", "001", "CHARGE", "VAT", ACCOUNT, false);
        cso = new StaffUser("CSO-1", "cso@bank.test", "Branch CSO", branch);
        ReflectionTestUtils.setField(cso, "id", 1L);
        when(security.user()).thenReturn(cso);
        when(branches.findByCodeForUpdate("001")).thenReturn(Optional.of(branch));
        when(managersChequeAccounts.resolve(ACCOUNT, "001")).thenReturn(
                new AccountDetails(ACCOUNT, "Manager's Cheque", "O", "2004", "NGN"));
        when(requests.save(any(ChequeRequest.class))).thenAnswer(invocation -> {
            ChequeRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 21L);
            return request;
        });
        when(history.findByRequestRequestNumberOrderByCreatedAtAsc(any())).thenReturn(List.of());
        service = new RequestService(requests, eligibility, history, charges, branches,
                mock(ChequeNumberRepository.class), coreBanking, security, audit, email,
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), "CREDIT", BigDecimal.valueOf(0.075), "NGN",
                managersChequeAccounts,
                mock(VendorWorkflowProperties.class));
    }

    @Test
    void createsManagersChequeWithNoDebitPolicy() {
        MapResult result = create(null);

        assertEquals("03", result.request().getChequeType().code());
        assertEquals(DebitPolicy.NOT_REQUIRED, result.request().getDebitPolicy());
        assertEquals(RequestStatus.PENDING_APPROVAL, result.request().getStatus());
        assertEquals("2004", result.request().getSchemeCode());
        assertEquals("NGN", result.request().getCurrency());
        assertEquals("Manager's Cheque", result.request().getAccountName());
        verify(managersChequeAccounts).resolve(ACCOUNT, "001");
        verifyNoInteractions(coreBanking);
        verify(eligibility, never()).save(any());
    }

    @Test
    void ignoresAClientSuppliedAccountAndUsesTheBranchConfiguration() {
        MapResult result = create("1111111111");

        assertEquals(ACCOUNT, result.request().getAccountNumber());
        verify(managersChequeAccounts).resolve(ACCOUNT, "001");
        verifyNoInteractions(coreBanking);
    }

    @Test
    void rejectsASecondActiveManagersChequeRequestForTheBranch() {
        when(requests.existsByBranchCodeIgnoreCaseAndChequeTypeAndStatusNotIn(
                any(), any(), any())).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(null, 50, "03"));

        assertEquals("ACTIVE_MANAGERS_CHEQUE_REQUEST_EXISTS", exception.getCode());
        verifyNoInteractions(managersChequeAccounts);
        verifyNoInteractions(coreBanking);
    }

    @Test
    void bomApprovalSkipsDebitAndMakesManagersChequeExportable() {
        ChequeRequest request = new ChequeRequest(ACCOUNT, "Head Office Manager's Cheque", "BANK", "MGR",
                ChequeType.MANAGERS_CHEQUE, "NGN", "001", "Head Office", "001", "Head Office",
                "1 Bank Street", "001", 50, cso);
        ReflectionTestUtils.setField(request, "id", 21L);
        request.assignRequestNumber("CBR-003");
        StaffUser bom = new StaffUser("BOM-1", "bom@bank.test", "Branch BOM", branch);
        ReflectionTestUtils.setField(bom, "id", 2L);
        when(requests.findByRequestNumberForUpdate("CBR-003")).thenReturn(Optional.of(request));
        when(security.user()).thenReturn(bom);
        when(security.principal()).thenReturn(new PortalPrincipal(2L, "bom", "bom@bank.test", "Branch BOM", "BOM",
                Set.of("BOM"), Set.of(), "001", "Head Office"));

        service.approve("CBR-003");

        assertEquals(RequestStatus.APPROVED, request.getStatus());
        verify(charges, never()).save(any(ChargeTransaction.class));
        verify(coreBanking, never()).postChequeBookCharge(any());
        verify(audit).record("MANAGERS_CHEQUE_APPROVED_NO_DEBIT", "CHEQUE_REQUEST", "CBR-003",
                "Manager's Cheque request approved without customer debit.");
    }

    private MapResult create(String submittedAccount) {
        service.create(submittedAccount, 50, "03");
        ArgumentCaptor<ChequeRequest> captor = ArgumentCaptor.forClass(ChequeRequest.class);
        verify(requests).save(captor.capture());
        return new MapResult(captor.getValue());
    }

    private record MapResult(ChequeRequest request) {}
}
