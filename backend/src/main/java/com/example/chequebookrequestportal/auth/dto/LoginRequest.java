package com.example.chequebookrequestportal.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @NotBlank @Size(max = 200) String username,
        @NotBlank @Size(max = 200) String password) {
}
