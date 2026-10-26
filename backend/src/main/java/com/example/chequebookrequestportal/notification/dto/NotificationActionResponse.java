package com.example.chequebookrequestportal.notification.dto;

import com.example.chequebookrequestportal.notification.NotificationActionCode;

public record NotificationActionResponse(NotificationActionCode code, long count) {
}
