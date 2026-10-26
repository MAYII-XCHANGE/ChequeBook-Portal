package com.example.chequebookrequestportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "cheque_staff_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_staff_email", columnNames = "email")
})
public class StaffUser extends BaseEntity {
    @Column(length = 50)
    private String employeeId;
    @Column(nullable = false, length = 200)
    private String email;
    @Column(nullable = false, length = 200)
    private String fullName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthSource authSource;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private Long tokenVersion = 0L;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "cheque_staff_roles", joinColumns = @JoinColumn(name = "staff_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<PortalRole> roles = new LinkedHashSet<>();

    protected StaffUser() {
    }

    public StaffUser(String employeeId, String email, String fullName, Branch branch) {
        this.employeeId = employeeId;
        this.email = email;
        this.fullName = fullName;
        this.authSource = AuthSource.LDAP;
        this.branch = branch;
        this.tokenVersion = 0L;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public AuthSource getAuthSource() {
        return authSource;
    }

    public boolean isActive() {
        return active;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public Branch getBranch() {
        return branch;
    }

    public Set<PortalRole> getRoles() {
        return Set.copyOf(roles);
    }

    public void update(String employeeId, String email, String fullName, Branch branch) {
        this.employeeId = employeeId;
        this.email = email;
        this.fullName = fullName;
        this.branch = branch;
    }

    public void replaceRoles(Set<PortalRole> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void assignBranch(Branch branch) {
        this.branch = branch;
    }

    public void invalidateTokens() {
        this.tokenVersion = (tokenVersion == null ? 0L : tokenVersion) + 1;
    }
}
