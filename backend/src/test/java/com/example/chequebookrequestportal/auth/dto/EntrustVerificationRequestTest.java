package com.example.chequebookrequestportal.auth.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class EntrustVerificationRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTokensBetweenSixAndTenDigits() {
        assertTrue(validator.validate(request("123456")).isEmpty());
        assertTrue(validator.validate(request("12345678")).isEmpty());
        assertTrue(validator.validate(request("1234567890")).isEmpty());
    }

    @Test
    void rejectsTokensOutsideTheRangeOrContainingNonDigits() {
        assertFalse(validator.validate(request("12345")).isEmpty());
        assertFalse(validator.validate(request("12345678901")).isEmpty());
        assertFalse(validator.validate(request("12345A")).isEmpty());
    }

    private EntrustVerificationRequest request(String token) {
        return new EntrustVerificationRequest("challenge-1", token);
    }
}
