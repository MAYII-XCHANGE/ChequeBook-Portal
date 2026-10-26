package com.example.chequebookrequestportal.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.corebanking.dto.response.InternalTransferResponse;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.ChargeTransaction;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeType;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RequestServiceApprovalTest {
    @Test
    void bomApprovalStopsAfterSuccessfulDebitAndMakesRequestExportable() {
        ChequeRequestRepository requests = mock(ChequeRequestRepository.class);
        ChargeTransactionRepository charges = mock(ChargeTransactionRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        RequestHistoryRepository history = mock(RequestHistoryRepository.class);
        CoreBankingService coreBanking = mock(CoreBankingService.class);
        SecurityService security = mock(SecurityService.class);
        Branch branch = new Branch("001", "Head Office", "1 Bank Street", "001", "CHARGE", "VAT");
        StaffUser maker = new StaffUser("CSO-1", "cso@bank.test", "Branch CSO", branch);
        StaffUser bom = new StaffUser("BOM-1", "bom@bank.test", "Branch BOM", branch);
        ReflectionTestUtils.setField(maker, "id", 1L);
        ReflectionTestUtils.setField(bom, "id", 2L);
        ChequeRequest request = new ChequeRequest("0123456789", "Customer", "SINGLE", "CUR",
                ChequeType.INDIVIDUAL_CURRENT, "NGN", null, null, "001", "Head Office", "1 Bank Street", "001",
                50, maker);
        ReflectionTestUtils.setField(request, "id", 11L);
        request.assignRequestNumber("CBR-001");
        when(requests.findByRequestNumberForUpdate("CBR-001")).thenReturn(Optional.of(request));
        when(branches.findByCodeIgnoreCase("001")).thenReturn(Optional.of(branch));
        when(security.user()).thenReturn(bom);
        when(security.principal()).thenReturn(new PortalPrincipal(2L, "bom", "bom@bank.test", "Branch BOM", "BOM",
                Set.of("BOM"), Set.of(), "001", "Head Office"));
        when(charges.save(any(ChargeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(charges.findByRequestRequestNumber("CBR-001")).thenReturn(Optional.empty());
        when(history.findByRequestRequestNumberOrderByCreatedAtAsc("CBR-001")).thenReturn(List.of());
        when(coreBanking.postChequeBookCharge(any())).thenReturn(new InternalTransferResponse(
                "TX-1", null, null, null, null, "SUCC", "00", "Successful"));
        RequestService service = new RequestService(requests, mock(EligibilityRepository.class), history, charges,
                branches, mock(ChequeNumberRepository.class), coreBanking, security, mock(AuditService.class),
                mock(WorkflowEmailService.class), BigDecimal.valueOf(100), BigDecimal.valueOf(200), "CREDIT",
                BigDecimal.valueOf(0.075), "NGN");

        service.approve("CBR-001");

        assertEquals(RequestStatus.APPROVED, request.getStatus());
        verify(coreBanking).postChequeBookCharge(any());
        ArgumentCaptor<ChargeTransaction> transaction = ArgumentCaptor.forClass(ChargeTransaction.class);
        verify(charges).save(transaction.capture());
        assertEquals(DebitStatus.SUCCESSFUL, transaction.getValue().getStatus());
    }
}
