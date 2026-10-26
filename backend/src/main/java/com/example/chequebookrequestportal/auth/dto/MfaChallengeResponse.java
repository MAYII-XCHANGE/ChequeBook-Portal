package com.example.chequebookrequestportal.auth.dto;

public record MfaChallengeResponse(String challengeId, long expiresIn) {
}
