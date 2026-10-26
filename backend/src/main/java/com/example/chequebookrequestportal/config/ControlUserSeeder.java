package com.example.chequebookrequestportal.config;

import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.PortalRole;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.BranchRepository;
import com.example.chequebookrequestportal.repository.RoleRepository;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ControlUserSeeder implements CommandLineRunner {

    private final BranchRepository branches;
    private final RoleRepository roles;
    private final StaffUserRepository users;
    private final String adminEmail;
    private final String employeeId;
    private final String fullName;

    private final String branchCode;
    private final String branchName;
    private final String branchAddress;
    private final String branchSortCode;
    private final String branchChargeGlAccount;
    private final String branchVatGlAccount;

    public ControlUserSeeder(
            BranchRepository branches,
            RoleRepository roles,
            StaffUserRepository users,
            @Value("${control-user-seeder.email:}") String adminEmail,
            @Value("${control-user-seeder.employee-id:SYSTEM-ADMIN}") String employeeId,
            @Value("${control-user-seeder.full-name:Portal Administrator}") String fullName,

            @Value("${app.system-branches.head-office.code}") String branchCode,
            @Value("${app.system-branches.head-office.name}") String branchName,
            @Value("${app.system-branches.head-office.address:}") String branchAddress,
            @Value("${app.system-branches.head-office.sort-code:}") String branchSortCode,
            @Value("${app.system-branches.head-office.charge-gl-account}") String branchChargeGlAccount,
            @Value("${app.system-branches.head-office.vat-gl-account}") String branchVatGlAccount) {

        this.branches = branches;
        this.roles = roles;
        this.users = users;

        this.adminEmail = adminEmail;
        this.employeeId = employeeId;
        this.fullName = fullName;

        this.branchCode = branchCode;
        this.branchName = branchName;
        this.branchAddress = branchAddress;
        this.branchSortCode = branchSortCode;
        this.branchChargeGlAccount = branchChargeGlAccount;
        this.branchVatGlAccount = branchVatGlAccount;
    }

    @Override
    @Transactional
    public void run(String... args) {

        validateConfiguration();

        Branch branch = maintainHeadOfficeBranch();

        Map<String, Set<String>> definitions =
                Map.of(
                        "CSO",
                        Set.of(
                                Permissions.CREATE_REQUEST,
                                Permissions.VIEW_REQUEST,
                                Permissions.VIEW_REPORTS,
                                Permissions.DELIVER_REQUEST
                        ),

                        "BOM",
                        Set.of(
                                Permissions.VIEW_REQUEST,
                                Permissions.APPROVE_REQUEST,
                                Permissions.VIEW_REPORTS
                        ),

                        "OPERATIONS",
                        Set.of(
                                Permissions.VIEW_REQUEST,
                                Permissions.PROCESS_REQUEST,
                                Permissions.EXPORT_VENDOR,
                                Permissions.VIEW_REPORTS,
                                Permissions.VIEW_AUDIT
                        ),

                        "AUDITOR",
                        Set.of(
                                Permissions.VIEW_REPORTS,
                                Permissions.VIEW_AUDIT
                        ),

                        "ADMIN",
                        Permissions.ADMIN_ONLY
                );

        definitions.forEach(this::maintainSystemRole);

        seedControlUser(branch);
    }

    private void maintainSystemRole(String code, Set<String> permissions) {
        PortalRole role = roles.findByCodeIgnoreCase(code)
                .orElseGet(() -> new PortalRole(code, roleName(code), true, permissions));
        role.updateName(roleName(code));
        if ("CSO".equals(code)) {
            Set<String> maintainedPermissions = new LinkedHashSet<>(role.getPermissions());
            maintainedPermissions.add(Permissions.VIEW_REPORTS);
            maintainedPermissions.add(Permissions.DELIVER_REQUEST);
            role.replacePermissions(maintainedPermissions);
        }
        roles.save(role);
    }

    private void seedControlUser(Branch branch) {
        PortalRole adminRole =
                roles.findByCodeIgnoreCase("ADMIN")
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "ADMIN role was not created"
                                )
                        );

        StaffUser admin = users.findByEmailIgnoreCase(adminEmail)
                .orElseGet(() -> new StaffUser(
                        employeeId,
                        adminEmail,
                        fullName,
                        branch
                ));

        admin.assignBranch(branch);
        admin.replaceRoles(Set.of(adminRole));
        users.save(admin);
    }

    private Branch maintainHeadOfficeBranch() {
        Branch branch = branches.findByCodeIgnoreCase(branchCode)
                .orElseGet(() -> new Branch(
                        branchCode,
                        branchName,
                        branchAddress,
                        branchSortCode,
                        branchChargeGlAccount,
                        branchVatGlAccount,
                        true
                ));

        branch.maintainSystemDefinition(branchName, branchAddress, branchSortCode, branchChargeGlAccount,
                branchVatGlAccount);
        return branches.save(branch);
    }

    private void validateConfiguration() {

        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException(
                    "control-user-seeder.email is required"
            );
        }

        if (branchCode == null || branchCode.isBlank()) {
            throw new IllegalStateException(
                    "app.system-branches.head-office.code is required"
            );
        }

        if (branchName == null || branchName.isBlank()) {
            throw new IllegalStateException(
                    "app.system-branches.head-office.name is required"
            );
        }

        if (branchChargeGlAccount == null || branchChargeGlAccount.isBlank()) {
            throw new IllegalStateException(
                    "app.system-branches.head-office.charge-gl-account is required"
            );
        }

        if (branchVatGlAccount == null || branchVatGlAccount.isBlank()) {
            throw new IllegalStateException(
                    "app.system-branches.head-office.vat-gl-account is required"
            );
        }

        if (branchChargeGlAccount.equalsIgnoreCase(branchVatGlAccount)) {
            throw new IllegalStateException(
                    "Head-office charge GL and VAT GL accounts must be different"
            );
        }
    }

    private String roleName(String code) {

        return switch (code) {

            case "CSO" ->
                    "Customer Service Officer";

            case "BOM" ->
                    "Branch Operations Manager";

            case "OPERATIONS" ->
                    "Clearing Operations";

            case "AUDITOR" ->
                    "Auditor";

            case "ADMIN" ->
                    "System Administrator";

            default ->
                    "Portal User";
        };
    }
}
