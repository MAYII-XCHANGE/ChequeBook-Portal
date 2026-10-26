package com.example.chequebookrequestportal.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chequebookrequestportal.domain.Branch;
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
class AdministrationEmailServiceTest {
    @Mock
    private StaffUserRepository users;
    @Mock
    private EmailOutboxRepository outbox;
    @Mock
    private AdministrationEmailTemplateRenderer templateRenderer;

    @Test
    void branchChangeIsQueuedForActiveBranchBomsWithoutSensitiveValues() {
        Branch branch = new Branch("001", "Victoria Island", "Address", "SORT-A", "CHARGE-GL", "VAT-GL");
        StaffUser recipient = new StaffUser("BOM-1", "bom@novabank.ng", "Branch BOM", branch);
        when(users.findActiveByRoleAndBranchIncludingInactiveBranch("BOM", "001"))
                .thenReturn(List.of(recipient));
        when(templateRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn("<html>Notification</html>");
        AdministrationEmailService service = new AdministrationEmailService(users, outbox, templateRenderer, true,
                "https://portal.example/login", "[Chequebook Portal]");

        service.branchModified(branch, List.of("Cheque charge GL account updated"), "System Administrator");

        ArgumentCaptor<AdministrationEmailTemplateModel> model = ArgumentCaptor
                .forClass(AdministrationEmailTemplateModel.class);
        verify(templateRenderer).render(model.capture());
        assertThat(model.getValue().changes()).containsExactly("Cheque charge GL account updated");
        assertThat(model.getValue().changes()).noneMatch(change -> change.contains("CHARGE-GL"));

        ArgumentCaptor<EmailOutboxMessage> message = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outbox).save(message.capture());
        assertThat(message.getValue().getEventType()).isEqualTo(EmailEventType.ADMIN_BRANCH_MODIFIED);
        assertThat(message.getValue().getRecipientAddress()).isEqualTo("bom@novabank.ng");
        assertThat(message.getValue().getRequestNumber()).isEqualTo("BRANCH-001");
    }
}
