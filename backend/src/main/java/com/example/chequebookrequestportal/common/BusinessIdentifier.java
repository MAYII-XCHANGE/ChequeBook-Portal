package com.example.chequebookrequestportal.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Generates stable business-facing identifiers from database-generated IDs. */
public final class BusinessIdentifier {
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private BusinessIdentifier() {
    }

    public static String chequeRequest(LocalDate date, String servicingBranchCode, Long databaseId) {
        if (date == null)
            throw new IllegalArgumentException("Request date is required.");
        return "CBR-" + date.getYear() + "-" + normalizeBranchCode(servicingBranchCode) + "-"
                + paddedId(databaseId);
    }

    public static String vendorExport(LocalDate date, Long databaseId) {
        if (date == null)
            throw new IllegalArgumentException("Export date is required.");
        return "VEX-" + COMPACT_DATE.format(date) + "-" + paddedId(databaseId);
    }

    private static String normalizeBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank())
            throw new IllegalArgumentException("Servicing branch code is required.");
        String normalized = branchCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]+"))
            throw new IllegalArgumentException("Servicing branch code must be alphanumeric.");
        if (normalized.matches("[0-9]{1,3}"))
            return String.format(Locale.ROOT, "%03d", Integer.parseInt(normalized));
        return normalized;
    }

    private static String paddedId(Long databaseId) {
        if (databaseId == null || databaseId <= 0)
            throw new IllegalArgumentException("A persisted database ID is required.");
        return String.format(Locale.ROOT, "%06d", databaseId);
    }
}
