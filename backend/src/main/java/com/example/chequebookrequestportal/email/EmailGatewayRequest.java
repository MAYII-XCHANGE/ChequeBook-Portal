package com.example.chequebookrequestportal.email;

import java.util.List;

public record EmailGatewayRequest(
        String channel,
        String subject,
        String message,
        String displayName,
        List<Recipient> to,
        List<Recipient> cc,
        List<Object> attachments) {

    public record Recipient(String name, String email) {
    }
}
