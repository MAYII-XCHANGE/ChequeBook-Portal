package com.example.chequebookrequestportal.email;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
public class AdministrationEmailTemplateRenderer {
    private final SpringTemplateEngine templateEngine;

    public AdministrationEmailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(AdministrationEmailTemplateModel model) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("recipientName", model.recipientName());
        context.setVariable("notificationTitle", model.notificationTitle());
        context.setVariable("notificationMessage", model.notificationMessage());
        context.setVariable("entityType", model.entityType());
        context.setVariable("entityReference", model.entityReference());
        context.setVariable("affectedBranches", model.affectedBranches());
        context.setVariable("changes", model.changes());
        context.setVariable("changedBy", model.changedBy());
        context.setVariable("changedAt", model.changedAt());
        context.setVariable("portalUrl", model.portalUrl());
        return templateEngine.process("email/administration-notification", context);
    }
}
