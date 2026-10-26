package com.example.chequebookrequestportal.corebanking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CreditTransferRequest(
        @JsonProperty("tran_ref") String transactionReference,
        @JsonProperty("tran_date") String transactionDate,
        @JsonProperty("tran_ccy") String transactionCurrency,
        @JsonProperty("value_date") String valueDate,
        @JsonProperty("tran_sub_type") String transactionSubType,
        @JsonProperty("tran_type") String transactionType,
        @JsonProperty("ref_number") String referenceNumber,
        @JsonProperty("part_tran") List<PartTransaction> partTransactions) {

    public record PartTransaction(
            @JsonProperty("acct_no") String accountNumber,
            String amount,
            String remark,
            String type,
            String narration,
            @JsonProperty("tran_type") String transactionType) {
    }
}
