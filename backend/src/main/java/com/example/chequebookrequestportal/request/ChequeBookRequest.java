package com.example.chequebookrequestportal.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChequeBookRequest(
        @JsonProperty("LeavesPerBk") int leavesPerBook,
        @JsonProperty("AckObtainedFlg") String acknowledgementObtainedFlag,
        @JsonProperty("AcctId") String accountId,
        @JsonProperty("InvtReqd") String inventoryRequired,
        @JsonProperty("TotNumOfLeaves") int totalNumberOfLeaves,
        @JsonProperty("BeginChqNum") String beginningChequeNumber,
        @JsonProperty("SolId") String solId) {
}
