package com.example.chequebookrequestportal.email;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
public class WorkflowEmailTemplateRenderer {
    private final SpringTemplateEngine templateEngine;

    public WorkflowEmailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(WorkflowEmailTemplateModel model) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("recipientName", model.recipientName());
        context.setVariable("notificationTitle", model.notificationTitle());
        context.setVariable("notificationMessage", model.notificationMessage());
        context.setVariable("requestReference", model.requestReference());
        context.setVariable("branch", model.branch());
        context.setVariable("accountName", model.accountName());
        context.setVariable("maskedAccountNumber", model.maskedAccountNumber());
        context.setVariable("currentStatus", model.currentStatus());
        context.setVariable("portalUrl", model.portalUrl());
        return templateEngine.process("email/workflow-notification", context);
    }
}
