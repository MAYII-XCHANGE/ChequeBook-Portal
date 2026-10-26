package com.example.chequebookrequestportal.email;

@FunctionalInterface
public interface EmailGatewayClient {
    void send(EmailOutboxMessage message);
}
