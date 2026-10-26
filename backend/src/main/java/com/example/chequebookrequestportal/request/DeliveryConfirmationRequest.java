package com.example.chequebookrequestportal.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record DeliveryConfirmationRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "Account number confirmation must contain exactly four digits.")
        String accountLastFour,
        @NotNull
        @AssertTrue(message = "Customer identity must be verified before delivery.")
        Boolean identityVerified,
        @NotNull
        @AssertTrue(message = "Cheque book handover must be confirmed before delivery.")
        Boolean handoverConfirmed,
        @NotNull
        @PositiveOrZero
        Long requestVersion) {
}
