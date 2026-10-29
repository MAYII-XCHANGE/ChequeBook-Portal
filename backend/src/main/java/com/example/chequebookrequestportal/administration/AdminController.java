package com.example.chequebookrequestportal.administration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    List<Map<String, Object>> users() {
        return service.users();
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    Map<String, Object> user(@PathVariable Long id) {
        return service.user(id);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    Map<String, Object> createUser(@Valid @RequestBody UserInput i) {
        return service.createUser(i);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    Map<String, Object> updateUser(@PathVariable Long id, @Valid @RequestBody UserInput i) {
        return service.updateUser(id, i);
    }

    @PostMapping("/users/{id}/activate")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    void activateUser(@PathVariable Long id) {
        service.setUserActive(id, true);
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    void deactivateUser(@PathVariable Long id) {
        service.setUserActive(id, false);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT')")
    Map<String, Object> updateUserRoles(@PathVariable Long id, @Valid @RequestBody RoleAssignment i) {
        return service.updateUserRoles(id, i.roles());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    List<Map<String, Object>> roles() {
        return service.roles();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    Map<String, Object> createRole(@Valid @RequestBody RoleInput i) {
        return service.createRole(i);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    Map<String, Object> updateRole(@PathVariable Long id, @Valid @RequestBody RoleInput i) {
        return service.updateRole(id, i);
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    Map<String, Object> updatePermissions(@PathVariable Long id, @Valid @RequestBody PermissionAssignment i) {
        return service.updateRolePermissions(id, i.permissions());
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('BRANCH_MANAGEMENT')")
    List<Map<String, Object>> branches() {
        return service.branches();
    }

    @PostMapping("/branches")
    @PreAuthorize("hasAuthority('BRANCH_MANAGEMENT')")
    Map<String, Object> createBranch(@Valid @RequestBody BranchInput i) {
        return service.createBranch(i);
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGEMENT')")
    Map<String, Object> updateBranch(@PathVariable Long id, @Valid @RequestBody BranchInput i) {
        return service.updateBranch(id, i);
    }

    @PostMapping("/branches/{id}/activate")
    @PreAuthorize("hasAuthority('BRANCH_MANAGEMENT')")
    void activateBranch(@PathVariable Long id) {
        service.setBranchActive(id, true);
    }

    @PostMapping("/branches/{id}/deactivate")
    @PreAuthorize("hasAuthority('BRANCH_MANAGEMENT')")
    void deactivateBranch(@PathVariable Long id) {
        service.setBranchActive(id, false);
    }

    public record UserInput(@NotBlank @Size(max = 50) String employeeId,
            @Email @NotBlank @Size(max = 200) String email,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 20) String branchCode,
            @NotEmpty Set<String> roles) {
    }

    public record RoleInput(@NotBlank String code, @NotBlank String name, @NotEmpty Set<String> permissions) {
    }

    public record BranchInput(@NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String address,
            @NotBlank @Size(max = 30) String sortCode,
            @NotBlank @Size(max = 30) String chargeGlAccount,
            @NotBlank @Size(max = 30) String vatGlAccount,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "Manager's Cheque account must contain 10 digits.")
            String managersChequeAccount) {
    }

    public record RoleAssignment(@NotEmpty Set<String> roles) {
    }

    public record PermissionAssignment(@NotEmpty Set<String> permissions) {
    }
}
