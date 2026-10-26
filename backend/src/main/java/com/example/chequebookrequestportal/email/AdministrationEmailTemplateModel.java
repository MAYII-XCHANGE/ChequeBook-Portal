package com.example.chequebookrequestportal.email;

import java.util.List;

public record AdministrationEmailTemplateModel(
        String recipientName,
        String notificationTitle,
        String notificationMessage,
        String entityType,
        String entityReference,
        String affectedBranches,
        List<String> changes,
        String changedBy,
        String changedAt,
        String portalUrl) {
}
