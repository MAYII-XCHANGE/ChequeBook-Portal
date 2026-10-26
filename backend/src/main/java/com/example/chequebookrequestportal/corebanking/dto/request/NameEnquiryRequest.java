package com.example.chequebookrequestportal.corebanking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NameEnquiryRequest(
        @JsonProperty("acct_no")
        @NotBlank
        @Pattern(regexp = "\\d{10}")
        String accountNumber) {
}
