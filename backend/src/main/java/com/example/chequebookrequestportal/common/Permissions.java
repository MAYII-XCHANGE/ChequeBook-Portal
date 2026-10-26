package com.example.chequebookrequestportal.common;

import java.util.Set;

public final class Permissions {
    public static final String CREATE_REQUEST = "CREATE_CHEQUE_REQUEST", VIEW_REQUEST = "VIEW_CHEQUE_REQUEST",
            APPROVE_REQUEST = "APPROVE_CHEQUE_REQUEST", PROCESS_REQUEST = "PROCESS_CHEQUE_REQUEST",
            DELIVER_REQUEST = "DELIVER_CHEQUE_REQUEST",
            EXPORT_VENDOR = "EXPORT_VENDOR", VIEW_REPORTS = "VIEW_REPORTS", VIEW_AUDIT = "VIEW_AUDIT_TRAIL",
            USER_MANAGEMENT = "USER_MANAGEMENT", ROLE_MANAGEMENT = "ROLE_MANAGEMENT",
            BRANCH_MANAGEMENT = "BRANCH_MANAGEMENT";
    public static final Set<String> ADMIN_ONLY = Set.of(USER_MANAGEMENT, ROLE_MANAGEMENT, BRANCH_MANAGEMENT);

    private Permissions() {
    }
}
