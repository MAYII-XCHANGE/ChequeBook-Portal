package com.example.chequebookrequestportal.notification;

import com.example.chequebookrequestportal.notification.dto.NotificationSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    NotificationSummaryResponse summary() {
        return service.summary();
    }
}
