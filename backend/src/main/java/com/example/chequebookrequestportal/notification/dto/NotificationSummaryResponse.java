package com.example.chequebookrequestportal.notification.dto;

import java.time.Instant;
import java.util.List;

public record NotificationSummaryResponse(long totalCount, List<NotificationActionResponse> actions,
        Instant generatedAt) {
}
