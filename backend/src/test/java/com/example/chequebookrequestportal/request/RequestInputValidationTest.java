package com.example.chequebookrequestportal.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RequestInputValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @MethodSource("supportedChequeTypes")
    void acceptsSupportedChequeTypes(String chequeType) {
        var input = new RequestController.CreateRequest("0123456789", 50, chequeType);

        assertThat(validator.validate(input)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("unsupportedChequeTypes")
    void rejectsMissingAndUnsupportedChequeTypes(String chequeType) {
        var input = new RequestController.CreateRequest("0123456789", 50, chequeType);

        assertThat(validator.validate(input))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("chequeType"));
    }

    @Test
    void rejectsNullChequeType() {
        var input = new RequestController.CreateRequest("0123456789", 50, null);

        assertThat(validator.validate(input))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("chequeType"));
    }

    private static Stream<String> supportedChequeTypes() {
        return Stream.of("01", "02");
    }

    private static Stream<String> unsupportedChequeTypes() {
        return Stream.of("", " ", "03", "99", "INDIVIDUAL");
    }
}
