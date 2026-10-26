package com.example.chequebookrequestportal.auth.dto;

public record AuthResponse(String username, String role, String accessToken, String refreshToken) {
}
