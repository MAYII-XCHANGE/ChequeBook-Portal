package com.example.chequebookrequestportal.corebanking.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditTransferResponse(
        @JsonAlias({ "tran_ref", "transactionReference" }) String transactionReference,
        @JsonAlias({ "reqId", "requestId" }) String requestId,
        @JsonAlias({ "tran_id", "transactionId" }) String transactionId,
        @JsonAlias({ "msg_date", "messageDate" }) String messageDate,
        @JsonAlias({ "tran_date", "transactionDate" }) String transactionDate,
        Status status) {

    public boolean successful() {
        return status != null
                && "SUCC".equalsIgnoreCase(status.type())
                && "00".equals(status.code());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String type, String code, String message) {
    }
}
