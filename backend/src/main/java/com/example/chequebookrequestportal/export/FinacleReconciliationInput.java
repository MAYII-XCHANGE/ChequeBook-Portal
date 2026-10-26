package com.example.chequebookrequestportal.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinacleReconciliationInput(
        @NotNull Outcome outcome,
        @NotBlank @Size(max = 100) String evidenceReference,
        @NotBlank @Size(min = 10, max = 1000) String note) {
    public enum Outcome {
        REGISTERED,
        NOT_REGISTERED
    }
}
