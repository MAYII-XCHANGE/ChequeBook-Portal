package com.example.chequebookrequestportal.corebanking.dto.request;

import java.math.BigDecimal;

public record InternalTransferRequest(
        String debitAccount,
        String creditAccount,
        BigDecimal amount,
        String referenceNumber,
        String remark,
        String narration) {
}
