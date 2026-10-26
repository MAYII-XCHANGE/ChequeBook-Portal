package com.example.chequebookrequestportal.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChequeBookResponse(boolean status, String message, Data data) {

    public boolean hostSuccessful() {
        return data != null && "SUCCESS".equalsIgnoreCase(data.hostTransactionStatus());
    }

    public String failureMessage() {
        if (data != null && data.errorDescription() != null && !data.errorDescription().isBlank())
            return data.errorDescription();
        return message == null || message.isBlank() ? "Cheque book request failed." : message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String globalUUID,
            String requestUUID,
            String serviceRequestId,
            String serviceId,
            String serviceRequestVersion,
            String serviceVersion,
            String channelId,
            String originatorId,
            String bankId,
            String timeZone,
            String messageDateTime,
            String ubuTransactionId,
            String ubuTransactionStatus,
            String hostTransactionId,
            String hostTransactionStatus,
            String hostParentTransactionId,
            String hostParentTransactionStatus,
            String errorCode,
            String errorDescription) {
    }
}
