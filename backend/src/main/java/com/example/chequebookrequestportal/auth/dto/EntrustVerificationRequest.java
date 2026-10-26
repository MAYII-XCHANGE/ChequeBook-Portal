package com.example.chequebookrequestportal.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EntrustVerificationRequest(
        @NotBlank @Size(max = 200) String challengeId,
        @NotBlank @Pattern(regexp = "\\d{6,10}",
                message = "Entrust token must contain between 6 and 10 digits.") String token) {
}
