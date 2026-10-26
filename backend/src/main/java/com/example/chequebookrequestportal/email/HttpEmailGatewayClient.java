package com.example.chequebookrequestportal.email;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpEmailGatewayClient implements EmailGatewayClient {
    private final RestTemplate restTemplate;
    private final String endpointUrl;
    private final String channel;
    private final String displayName;

    public HttpEmailGatewayClient(RestTemplate restTemplate,
            @Value("${app.email.endpoint-url}") String endpointUrl,
            @Value("${app.email.channel:EMAIL}") String channel,
            @Value("${app.email.display-name}") String displayName) {
        this.restTemplate = restTemplate;
        this.endpointUrl = endpointUrl;
        this.channel = channel;
        this.displayName = displayName;
    }

    @Override
    public void send(EmailOutboxMessage message) {
        List<EmailGatewayRequest.Recipient> cc = message.getCcAddress() == null
                ? List.of()
                : List.of(new EmailGatewayRequest.Recipient("Clearing Operations", message.getCcAddress()));
        EmailGatewayRequest request = new EmailGatewayRequest(
                channel,
                message.getSubject(),
                message.getBody(),
                displayName,
                List.of(new EmailGatewayRequest.Recipient(message.getRecipientName(), message.getRecipientAddress())),
                cc,
                List.of());
        restTemplate.postForEntity(endpointUrl, request, Void.class);
    }
}
