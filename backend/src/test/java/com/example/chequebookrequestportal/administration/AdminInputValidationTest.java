package com.example.chequebookrequestportal.administration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AdminInputValidationTest {
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

    @Test
    void branchRequiresAddressAndSortCodeWithoutRestrictingSortCodeFormat() {
        AdminController.BranchInput valid = new AdminController.BranchInput(
                "001", "Victoria Island", "23 Kofo Abayomi Street", "SORT-CODE-A",
                "CHARGE-GL", "VAT-GL", "0123456789");
        assertThat(validator.validate(valid)).isEmpty();

        AdminController.BranchInput missing = new AdminController.BranchInput(
                "001", "Victoria Island", " ", " ", "CHARGE-GL", "VAT-GL", "0123456789");
        assertThat(validator.validate(missing)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("address", "sortCode");
    }

    @Test
    void branchRequiresATenDigitManagersChequeAccount() {
        AdminController.BranchInput input = new AdminController.BranchInput(
                "001", "Victoria Island", "23 Kofo Abayomi Street", "SORT-CODE-A",
                "CHARGE-GL", "VAT-GL", "12345");

        assertThat(validator.validate(input)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("managersChequeAccount");
    }

    @Test
    void userRequiresABranchAssignment() {
        AdminController.UserInput input = new AdminController.UserInput(
                "EMP-001", "user@novabank.ng", "Portal User", " ", Set.of("CSO"));

        assertThat(validator.validate(input)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("branchCode");
    }
}
