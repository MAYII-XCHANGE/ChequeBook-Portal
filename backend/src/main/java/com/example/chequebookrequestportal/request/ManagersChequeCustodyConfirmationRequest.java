package com.example.chequebookrequestportal.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ManagersChequeCustodyConfirmationRequest(
        @AssertTrue(message = "Managers Cheque must be verified.")
        Boolean stockVerified,
        @AssertTrue(message = "Managers Cheque custody must be confirmed.")
        Boolean custodyConfirmed,
        @NotNull(message = "The request version is required.")
        Long requestVersion) {
}
