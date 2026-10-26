package com.example.chequebookrequestportal.corebanking.dto.request;

import java.math.BigDecimal;

public record ChequeBookChargePosting(
        String customerAccount,
        String chargeGlAccount,
        String vatGlAccount,
        BigDecimal chargeAmount,
        BigDecimal vatAmount,
        String narration) {
}
