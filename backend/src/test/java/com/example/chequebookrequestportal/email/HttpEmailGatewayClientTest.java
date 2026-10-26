package com.example.chequebookrequestportal.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class HttpEmailGatewayClientTest {
    @Mock
    private RestTemplate restTemplate;

    @Test
    void mapsStoredCcAddressToGatewayRequest() {
        HttpEmailGatewayClient client = new HttpEmailGatewayClient(restTemplate, "https://mail.example/send",
                "EMAIL", "Chequebook Portal");
        EmailOutboxMessage message = new EmailOutboxMessage("deduplication-key", EmailEventType.OPERATIONS_EXPORT_READY,
                "CBR-000001", "operator@novabank.ng", "Operations User", "clearingoperations@novabank.ng",
                "Request ready", "<html>Notification</html>");

        client.send(message);

        ArgumentCaptor<EmailGatewayRequest> request = ArgumentCaptor.forClass(EmailGatewayRequest.class);
        verify(restTemplate).postForEntity(eq("https://mail.example/send"), request.capture(), eq(Void.class));
        assertThat(request.getValue().cc()).containsExactly(
                new EmailGatewayRequest.Recipient("Clearing Operations", "clearingoperations@novabank.ng"));
    }

    @Test
    void leavesGatewayCcListEmptyWhenMessageHasNoCcAddress() {
        HttpEmailGatewayClient client = new HttpEmailGatewayClient(restTemplate, "https://mail.example/send",
                "EMAIL", "Chequebook Portal");
        EmailOutboxMessage message = new EmailOutboxMessage("deduplication-key", EmailEventType.BRANCH_DISPATCHED,
                "CBR-000001", "branch.user@novabank.ng", "Branch User", null,
                "Request dispatched", "<html>Notification</html>");

        client.send(message);

        ArgumentCaptor<EmailGatewayRequest> request = ArgumentCaptor.forClass(EmailGatewayRequest.class);
        verify(restTemplate).postForEntity(eq("https://mail.example/send"), request.capture(), eq(Void.class));
        assertThat(request.getValue().cc()).isEmpty();
    }
}
