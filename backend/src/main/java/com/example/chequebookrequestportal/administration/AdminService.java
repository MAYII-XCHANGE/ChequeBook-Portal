package com.example.chequebookrequestportal.administration;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.config.CacheConfig;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.PortalRole;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.email.AdministrationEmailService;
import com.example.chequebookrequestportal.repository.BranchRepository;
import com.example.chequebookrequestportal.repository.RoleRepository;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import com.example.chequebookrequestportal.security.SecurityService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final StaffUserRepository users;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final SecurityService security;
    private final AuditService audit;
    private final AdministrationEmailService administrationEmail;

    public AdminService(StaffUserRepository users, RoleRepository roles, BranchRepository branches,
            SecurityService security, AuditService audit, AdministrationEmailService administrationEmail) {
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.security = security;
        this.audit = audit;
        this.administrationEmail = administrationEmail;
    }

    @Cacheable(CacheConfig.USERS)
    public List<Map<String, Object>> users() {
        assertAdministrator();
        return users.findAllByOrderByFullNameAsc().stream().map(this::userMap).toList();
    }

    public Map<String, Object> user(Long id) {
        assertAdministrator();
        return userMap(findUser(id));
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> createUser(AdminController.UserInput input) {
        assertAdministrator();
        assertAdminRoleNotAssigned(input.roles());

        Branch branch = branch(input.branchCode());

        StaffUser user = new StaffUser(
                input.employeeId().trim(),
                input.email().trim().toLowerCase(Locale.ROOT),
                input.fullName().trim(),
                branch
        );

        user.replaceRoles(roleSet(input.roles()));

        users.save(user);

        audit.record(
                "USER_CREATED",
                "STAFF_USER",
                String.valueOf(user.getId()),
                "LDAP portal user created."
        );

        return userMap(user);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> updateUser(Long id, AdminController.UserInput input) {
        assertAdministrator();
        StaffUser user = findUser(id);
        if (isControlAdministrator(user)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROTECTED_ADMIN",
                    "The system administrator profile cannot be edited.");
        }
        assertAdminRoleNotAssigned(input.roles());
        Branch previousBranch = user.getBranch();
        Branch branch = branch(input.branchCode());
        Set<PortalRole> selectedRoles = roleSet(input.roles());
        List<String> changes = userChanges(user, input, branch, selectedRoles);
        user.update(input.employeeId().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                input.fullName().trim(), branch);
        user.replaceRoles(selectedRoles);
        audit.record("USER_UPDATED", "STAFF_USER", String.valueOf(id),
                "Portal user profile and role assignments updated.");
        administrationEmail.userModified(user, affectedBranchCodes(previousBranch, branch), changes,
                security.actor());
        return userMap(user);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public void setUserActive(Long id, boolean active) {
        assertAdministrator();
        StaffUser user = findUser(id);
        if (!active && isControlAdministrator(user)) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "The final active administrator cannot be disabled.");
        }
        boolean statusChanged = user.isActive() != active;
        user.setActive(active);
        audit.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "STAFF_USER", String.valueOf(id),
                "Portal user access state changed.");
        if (statusChanged)
            administrationEmail.userModified(user, affectedBranchCodes(user.getBranch()),
                    List.of("User access status changed to " + (active ? "Active" : "Inactive")), security.actor());
    }

    @Cacheable(CacheConfig.ROLES)
    public List<Map<String, Object>> roles() {
        assertAdministrator();
        return roles.findAll().stream().map(this::roleMap).toList();
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.ROLES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> createRole(AdminController.RoleInput input) {
        assertAdministrator();
        String code = input.code().trim().toUpperCase();
        if ("ADMIN".equals(code))
            throw protectedRole();
        PortalRole role = roles.save(new PortalRole(code, input.name(), false, Set.copyOf(input.permissions())));
        audit.record("ROLE_CREATED", "PORTAL_ROLE", String.valueOf(role.getId()),
                "Custom portal role created: " + code + ".");
        return roleMap(role);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.ROLES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> updateRole(Long id, AdminController.RoleInput input) {
        assertAdministrator();
        PortalRole role = role(id);
        if ("ADMIN".equals(role.getCode())) {
            role.updateName(input.name());
            role.replacePermissions(Permissions.ADMIN_ONLY);
        } else {
            role.updateName(input.name());
            role.replacePermissions(Set.copyOf(input.permissions()));
        }
        audit.record("ROLE_UPDATED", "PORTAL_ROLE", String.valueOf(id),
                "Portal role permissions updated: " + role.getCode() + ".");
        return roleMap(role);
    }

    @Cacheable(CacheConfig.BRANCHES)
    public List<Map<String, Object>> branches() {
        assertAdministrator();
        return branches.findAll().stream().map(this::branchMap).toList();
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.BRANCHES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> createBranch(AdminController.BranchInput input) {
        assertAdministrator();
        assertBranchAccounts(input, null);
        if (branches.findByCodeIgnoreCase(input.code().trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_CODE_EXISTS",
                    "A branch with this code already exists.");
        }
        Branch branch = branches
                .save(new Branch(input.code().trim(), input.name().trim(), input.address().trim(),
                        input.sortCode().trim(),
                        normalizeGlAccount(input.chargeGlAccount()), normalizeGlAccount(input.vatGlAccount()),
                        input.managersChequeAccount().trim(), false));
        audit.record("BRANCH_CREATED", "BRANCH", String.valueOf(branch.getId()),
                "Branch created: " + branch.getCode() + ".");
        return branchMap(branch);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.BRANCHES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> updateBranch(Long id, AdminController.BranchInput input) {
        assertAdministrator();
        Branch branch = branches.findById(id).orElseThrow(() -> notFound("Branch"));
        assertBranchIsUserManaged(branch);
        assertBranchAccounts(input, id);
        List<String> changes = branchChanges(branch, input);
        branch.update(input.name().trim(), input.address().trim(), input.sortCode().trim(),
                normalizeGlAccount(input.chargeGlAccount()), normalizeGlAccount(input.vatGlAccount()),
                input.managersChequeAccount().trim());
        audit.record("BRANCH_UPDATED", "BRANCH", String.valueOf(id),
                "Branch details updated: " + branch.getCode() + ".");
        administrationEmail.branchModified(branch, changes, security.actor());
        return branchMap(branch);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.BRANCHES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public void setBranchActive(Long id, boolean active) {
        assertAdministrator();
        Branch branch = branches.findById(id).orElseThrow(() -> notFound("Branch"));
        assertBranchIsUserManaged(branch);
        boolean statusChanged = branch.isActive() != active;
        branch.setActive(active);
        audit.record(active ? "BRANCH_ACTIVATED" : "BRANCH_DEACTIVATED", "BRANCH", String.valueOf(id),
                "Branch access state changed: " + branch.getCode() + ".");
        if (statusChanged)
            administrationEmail.branchModified(branch,
                    List.of("Branch status changed to " + (active ? "Active" : "Inactive")), security.actor());
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> updateUserRoles(Long id, Set<String> roleCodes) {
        assertAdministrator();
        StaffUser user = findUser(id);
        if (isControlAdministrator(user))
            throw protectedRole();
        assertAdminRoleNotAssigned(roleCodes);
        Set<PortalRole> selectedRoles = roleSet(roleCodes);
        boolean rolesChanged = !roleCodes(user.getRoles()).equals(roleCodes(selectedRoles));
        user.replaceRoles(selectedRoles);
        audit.record("USER_ROLES_UPDATED", "STAFF_USER", String.valueOf(id), "Portal role assignments updated.");
        if (rolesChanged)
            administrationEmail.userModified(user, affectedBranchCodes(user.getBranch()),
                    List.of("Role assignment updated"), security.actor());
        return userMap(user);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.ROLES, CacheConfig.USERS, CacheConfig.PROFILES}, allEntries = true)
    public Map<String, Object> updateRolePermissions(Long id, Set<String> permissions) {
        assertAdministrator();
        PortalRole role = role(id);
        role.replacePermissions("ADMIN".equals(role.getCode()) ? Permissions.ADMIN_ONLY : Set.copyOf(permissions));
        audit.record("ROLE_PERMISSIONS_UPDATED", "PORTAL_ROLE", String.valueOf(id),
                "Portal permission mapping updated: " + role.getCode() + ".");
        return roleMap(role);
    }

    private void assertAdministrator() {
        if (!security.principal().permissions().containsAll(Permissions.ADMIN_ONLY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Administrator permissions are required.");
        }
    }

    private void assertAdminRoleNotAssigned(Set<String> codes) {
        if (codes.stream().anyMatch("ADMIN"::equalsIgnoreCase))
            throw protectedRole();
    }

    private ApiException protectedRole() {
        return new ApiException(HttpStatus.CONFLICT, "PROTECTED_ROLE",
                "ADMIN is reserved for the automatically provisioned control administrator.");
    }

    private boolean isControlAdministrator(StaffUser user) {
        return user.getRoles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()));
    }

    private void assertBranchIsUserManaged(Branch branch) {
        if (branch.isSystemDefined()) {
            throw new ApiException(HttpStatus.CONFLICT, "SYSTEM_BRANCH_PROTECTED",
                    "System-defined branches are maintained by the application and cannot be modified.");
        }
    }

    private void assertBranchAccounts(AdminController.BranchInput input, Long currentBranchId) {
        String chargeGlAccount = normalizeGlAccount(input.chargeGlAccount());
        String vatGlAccount = normalizeGlAccount(input.vatGlAccount());
        if (chargeGlAccount.equals(vatGlAccount)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_GL_ACCOUNTS_MUST_DIFFER",
                    "The branch charge GL and VAT GL accounts must be different.");
        }
        if (glAccountUsedByAnotherBranch(chargeGlAccount, currentBranchId)
                || glAccountUsedByAnotherBranch(vatGlAccount, currentBranchId)) {
            throw new ApiException(HttpStatus.CONFLICT, "BRANCH_GL_ACCOUNT_EXISTS",
                    "Each branch must use its own charge GL and VAT GL accounts.");
        }
        branches.findByManagersChequeAccount(input.managersChequeAccount().trim()).ifPresent(branch -> {
            if (currentBranchId == null || !currentBranchId.equals(branch.getId()))
                throw new ApiException(HttpStatus.CONFLICT, "MANAGERS_CHEQUE_ACCOUNT_EXISTS",
                        "The Manager's Cheque account is already assigned to another branch.");
        });
    }

    private boolean glAccountUsedByAnotherBranch(String account, Long currentBranchId) {
        return branches.findUsingGlAccount(account).stream()
                .anyMatch(branch -> currentBranchId == null || !currentBranchId.equals(branch.getId()));
    }

    private String normalizeGlAccount(String account) {
        return account.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> branchChanges(Branch branch, AdminController.BranchInput input) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "Branch name updated", branch.getName(), input.name().trim());
        addChange(changes, "Branch address updated", branch.getAddress(), input.address().trim());
        addChange(changes, "Branch sort code updated", branch.getSortCode(), input.sortCode().trim());
        addChange(changes, "Cheque charge GL account updated", branch.getChargeGlAccount(),
                normalizeGlAccount(input.chargeGlAccount()));
        addChange(changes, "VAT GL account updated", branch.getVatGlAccount(),
                normalizeGlAccount(input.vatGlAccount()));
        addChange(changes, "Manager's Cheque account updated", branch.getManagersChequeAccount(),
                input.managersChequeAccount().trim());
        return changes;
    }

    private List<String> userChanges(StaffUser user, AdminController.UserInput input, Branch branch,
            Set<PortalRole> selectedRoles) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "Employee ID updated", user.getEmployeeId(), input.employeeId().trim());
        addChange(changes, "Email address updated", user.getEmail(),
                input.email().trim().toLowerCase(Locale.ROOT));
        addChange(changes, "Full name updated", user.getFullName(), input.fullName().trim());
        addChange(changes, "Branch assignment updated",
                user.getBranch() == null ? null : user.getBranch().getCode(), branch.getCode());
        if (!roleCodes(user.getRoles()).equals(roleCodes(selectedRoles)))
            changes.add("Role assignment updated");
        return changes;
    }

    private void addChange(List<String> changes, String description, String currentValue, String updatedValue) {
        if (!Objects.equals(normalized(currentValue), normalized(updatedValue)))
            changes.add(description);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private Set<String> roleCodes(Set<PortalRole> selectedRoles) {
        return selectedRoles.stream().map(PortalRole::getCode).map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Set<String> affectedBranchCodes(Branch... affectedBranches) {
        Set<String> codes = new LinkedHashSet<>();
        for (Branch branch : affectedBranches)
            if (branch != null && branch.getCode() != null && !branch.getCode().isBlank())
                codes.add(branch.getCode());
        return codes;
    }

    private StaffUser findUser(Long id) {
        return users.findById(id).orElseThrow(() -> notFound("User"));
    }

    private PortalRole role(Long id) {
        return roles.findById(id).orElseThrow(() -> notFound("Role"));
    }

    private Branch branch(String code) {
        return branches.findByCodeIgnoreCase(code).orElseThrow(() -> notFound("Branch"));
    }

    private Set<PortalRole> roleSet(Set<String> codes) {
        return codes.stream().map(code -> roles.findByCodeIgnoreCase(code).orElseThrow(() -> notFound("Role")))
                .collect(Collectors.toSet());
    }

    private ApiException notFound(String value) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", value + " not found.");
    }

    private Map<String, Object> userMap(StaffUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("employeeId", user.getEmployeeId() == null ? "" : user.getEmployeeId());
        result.put("email", user.getEmail());
        result.put("fullName", user.getFullName());
        result.put("authSource", user.getAuthSource());
        result.put("active", user.isActive());
        result.put("createdAt", user.getCreatedAt());
        result.put("branchCode", user.getBranch() == null ? "" : user.getBranch().getCode());
        result.put("roles", user.getRoles().stream().map(PortalRole::getCode).sorted().toList());
        return result;
    }

    private Map<String, Object> roleMap(PortalRole role) {
        return Map.of("id", role.getId(), "code", role.getCode(), "name", role.getName(), "systemRole",
                role.isSystemRole(), "permissions", role.getPermissions());
    }

    private Map<String, Object> branchMap(Branch branch) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", branch.getId());
        result.put("code", branch.getCode());
        result.put("name", branch.getName());
        result.put("address", branch.getAddress() == null ? "" : branch.getAddress());
        result.put("sortCode", branch.getSortCode() == null ? "" : branch.getSortCode());
        result.put("chargeGlAccount", branch.getChargeGlAccount() == null ? "" : branch.getChargeGlAccount());
        result.put("vatGlAccount", branch.getVatGlAccount() == null ? "" : branch.getVatGlAccount());
        result.put("managersChequeAccount",
                branch.getManagersChequeAccount() == null ? "" : branch.getManagersChequeAccount());
        result.put("active", branch.isActive());
        result.put("systemDefined", branch.isSystemDefined());
        result.put("createdAt", branch.getCreatedAt());
        return result;
    }
}
