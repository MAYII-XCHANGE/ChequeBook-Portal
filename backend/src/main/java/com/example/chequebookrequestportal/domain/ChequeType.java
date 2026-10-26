package com.example.chequebookrequestportal.domain;

import java.util.Arrays;

/** Cheque types currently supported by the portal and vendor interface. */
public enum ChequeType {
    INDIVIDUAL_CURRENT("01", "Individual Current Account"),
    CORPORATE("02", "Corporate Account");

    private final String code;
    private final String label;

    ChequeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String displayName() {
        return code + " — " + label;
    }

    public static ChequeType fromCode(String code) {
        String normalized = code == null ? "" : code.trim();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported cheque type: " + normalized));
    }
}
