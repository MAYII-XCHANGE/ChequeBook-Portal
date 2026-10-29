package com.example.chequebookrequestportal.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.ChequeType;
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
    void managersChequeEmailsUseInternalStockWordingWithoutCustomerDebitOrCollectionClaims() {
        StaffUser bom = new StaffUser("BOM-1", "bom@novabank.ng", "Branch Manager", null);
        StaffUser cso = new StaffUser("CSO-1", "cso@novabank.ng", "Branch User", null);
        StaffUser operations = new StaffUser("OPS-1", "operator@novabank.ng", "Operations User", null);
        when(users.findActiveByRoleAndBranch("BOM", "001")).thenReturn(List.of(bom));
        when(users.findActiveByRoleAndBranch("CSO", "001")).thenReturn(List.of(cso));
        when(users.findActiveByRole("OPERATIONS")).thenReturn(List.of(operations));
        when(templateRenderer.render(any())).thenReturn("<html>Manager's Cheque notification</html>");

        WorkflowEmailService service = new WorkflowEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]", "clearingoperations@novabank.ng");
        ChequeRequest request = managersChequeRequest();

        service.requestCreated(request);
        service.requestTransitioned(request, RequestStatus.APPROVED);
        service.requestTransitioned(request, RequestStatus.DISPATCHED);
        service.requestTransitioned(request, RequestStatus.RECEIVED_BY_BRANCH);
        service.requestTransitioned(request, RequestStatus.DELIVERED);

        ArgumentCaptor<WorkflowEmailTemplateModel> models = ArgumentCaptor
                .forClass(WorkflowEmailTemplateModel.class);
        verify(templateRenderer, times(6)).render(models.capture());
        assertThat(models.getAllValues()).extracting(WorkflowEmailTemplateModel::notificationTitle)
                .containsExactly(
                        "Manager's Cheque Request Awaiting Approval",
                        "Manager's Cheque Request Ready for Vendor Export",
                        "Manager's Cheque Stock Dispatched to Branch",
                        "Manager's Cheque Stock Received at Branch",
                        "Manager's Cheque Branch Receipt Confirmed",
                        "Manager's Cheque Stock Handover Confirmed");
        assertThat(models.getAllValues()).extracting(WorkflowEmailTemplateModel::notificationMessage)
                .anyMatch(message -> message.contains("approved without customer debit"))
                .anyMatch(message -> message.contains("authorized internal handover and custody confirmation"))
                .anyMatch(message -> message.contains("handover into authorized branch custody"))
                .noneMatch(message -> message.contains("successfully debited"))
                .noneMatch(message -> message.contains("customer has collected"));
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

    private ChequeRequest managersChequeRequest() {
        ChequeRequest request = new ChequeRequest("1003000321", "Manager Cheque", "O", "2004",
                ChequeType.MANAGERS_CHEQUE, "NGN", "001", "Head Office", "001", "Head Office",
                "23 Kofo Abayomi Street", "561150075", 50, null);
        request.assignRequestNumber("CBR-MC-000001");
        return request;
    }
}
