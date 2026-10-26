package com.example.chequebookrequestportal.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.EmailOutboxRepository;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowEmailServiceTest {
    @Mock
    private StaffUserRepository users;
    @Mock
    private EmailOutboxRepository outbox;
    @Mock
    private WorkflowEmailTemplateRenderer templateRenderer;

    @Test
    void operationsEmailStoresConfiguredCcAddress() {
        StaffUser recipient = new StaffUser("OPS-1", "operator@novabank.ng", "Operations User", null);
        when(users.findActiveByRole("OPERATIONS")).thenReturn(List.of(recipient));
        when(templateRenderer.render(any())).thenReturn("<html>Notification</html>");

        WorkflowEmailService service = new WorkflowEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]", "clearingoperations@novabank.ng");
        ChequeRequest request = request();

        service.requestTransitioned(request, RequestStatus.APPROVED);

        ArgumentCaptor<WorkflowEmailTemplateModel> templateModel = ArgumentCaptor
                .forClass(WorkflowEmailTemplateModel.class);
        verify(templateRenderer).render(templateModel.capture());
        assertThat(templateModel.getValue().accountName()).isEqualTo("Customer");
        assertThat(templateModel.getValue().maskedAccountNumber()).isEqualTo("012****789");

        ArgumentCaptor<EmailOutboxMessage> message = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outbox).save(message.capture());
        assertThat(message.getValue().getRecipientAddress()).isEqualTo("operator@novabank.ng");
        assertThat(message.getValue().getCcAddress()).isEqualTo("clearingoperations@novabank.ng");
    }

    @Test
    void branchEmailDoesNotStoreOperationsCcAddress() {
        StaffUser recipient = new StaffUser("CSO-1", "branch.user@novabank.ng", "Branch User", null);
        when(users.findActiveByRoleAndBranch("CSO", "001")).thenReturn(List.of(recipient));
        when(templateRenderer.render(any())).thenReturn("<html>Notification</html>");

        WorkflowEmailService service = new WorkflowEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]", "clearingoperations@novabank.ng");
        ChequeRequest request = request();

        service.requestTransitioned(request, RequestStatus.DISPATCHED);

        ArgumentCaptor<EmailOutboxMessage> message = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outbox).save(message.capture());
        assertThat(message.getValue().getCcAddress()).isNull();
    }

    @Test
    void customerCollectionEmailIsQueuedForOperationsWithGroupCc() {
        StaffUser recipient = new StaffUser("OPS-1", "operator@novabank.ng", "Operations User", null);
        when(users.findActiveByRole("OPERATIONS")).thenReturn(List.of(recipient));
        when(templateRenderer.render(any())).thenReturn("<html>Collection confirmed</html>");

        WorkflowEmailService service = new WorkflowEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]", "clearingoperations@novabank.ng");

        service.requestTransitioned(request(), RequestStatus.DELIVERED);

        ArgumentCaptor<EmailOutboxMessage> message = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outbox).save(message.capture());
        assertThat(message.getValue().getEventType()).isEqualTo(EmailEventType.CUSTOMER_COLLECTION_CONFIRMED);
        assertThat(message.getValue().getRecipientAddress()).isEqualTo("operator@novabank.ng");
        assertThat(message.getValue().getCcAddress()).isEqualTo("clearingoperations@novabank.ng");
        assertThat(message.getValue().getSubject()).contains("Customer Chequebook Collection Confirmed");
    }

    @Test
    void unexpectedChequeEligibilityFlagIsQueuedForOperations() {
        StaffUser recipient = new StaffUser("OPS-1", "operator@novabank.ng", "Operations User", null);
        when(users.findActiveByRole("OPERATIONS")).thenReturn(List.of(recipient));
        when(templateRenderer.render(any())).thenReturn("<html>Eligibility review</html>");
        WorkflowEmailService service = new WorkflowEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]", "clearingoperations@novabank.ng");

        service.chequeEligibilityReviewRequired("1003000321", "NULL/BLANK");

        ArgumentCaptor<WorkflowEmailTemplateModel> templateModel = ArgumentCaptor
                .forClass(WorkflowEmailTemplateModel.class);
        verify(templateRenderer).render(templateModel.capture());
        assertThat(templateModel.getValue().maskedAccountNumber()).isEqualTo("100****321");
        ArgumentCaptor<EmailOutboxMessage> message = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outbox).save(message.capture());
        assertThat(message.getValue().getEventType())
                .isEqualTo(EmailEventType.OPERATIONS_CHEQUE_ELIGIBILITY_REVIEW);
        assertThat(message.getValue().getRecipientAddress()).isEqualTo("operator@novabank.ng");
    }

    private ChequeRequest request() {
        ChequeRequest request = new ChequeRequest("0123456789", "Customer", "SINGLE", "SAV", "NGN",
                "001", "Head Office", "23 Kofo Abayomi Street", "561150075", 50, null);
        request.assignRequestNumber("CBR-000001");
        return request;
    }
}
