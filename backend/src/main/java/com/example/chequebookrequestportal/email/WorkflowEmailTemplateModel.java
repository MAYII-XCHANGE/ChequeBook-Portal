package com.example.chequebookrequestportal.email;

public record WorkflowEmailTemplateModel(
        String recipientName,
        String notificationTitle,
        String notificationMessage,
        String requestReference,
        String branch,
        String accountName,
        String maskedAccountNumber,
        String currentStatus,
        String portalUrl) {
}
