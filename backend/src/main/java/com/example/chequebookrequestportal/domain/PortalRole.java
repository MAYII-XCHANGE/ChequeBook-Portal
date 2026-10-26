package com.example.chequebookrequestportal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "cheque_roles", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
public class PortalRole extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private boolean systemRole;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cheque_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false, length = 80)
    private Set<String> permissions = new LinkedHashSet<>();

    protected PortalRole() {
    }

    public PortalRole(String code, String name, boolean systemRole, Set<String> permissions) {
        this.code = code;
        this.name = name;
        this.systemRole = systemRole;
        this.permissions.addAll(permissions);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void replacePermissions(Set<String> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
