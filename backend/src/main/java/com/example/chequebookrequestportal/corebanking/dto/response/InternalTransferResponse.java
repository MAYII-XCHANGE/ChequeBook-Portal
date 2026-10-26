package com.example.chequebookrequestportal.corebanking.dto.response;

public record InternalTransferResponse(
        String transactionReference,
        String requestId,
        String transactionId,
        String messageDate,
        String transactionDate,
        String statusType,
        String responseCode,
        String responseMessage) {

    public boolean successful() {
        return "SUCC".equalsIgnoreCase(statusType) && "00".equals(responseCode);
    }
}
