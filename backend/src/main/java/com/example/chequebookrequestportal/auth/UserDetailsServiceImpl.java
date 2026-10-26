package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.domain.PortalRole;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl {

    private final StaffUserRepository users;

    public UserDetailsServiceImpl(StaffUserRepository users) {
        this.users = users;
    }

    public PortalPrincipal loadUserByUsername(String username) {

        StaffUser user =
                users.findByEmailIgnoreCase(username)
                        .filter(value ->
                                value.isActive()
                                        && (
                                        value.getBranch() == null
                                                || value.getBranch().isActive()
                                )
                        )
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "user not found"
                                        )
                        );

        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();

        for (PortalRole role : user.getRoles()) {
            roles.add(role.getCode());
            permissions.addAll(role.getPermissions());
        }

        String primaryRole =
                roles.stream()
                        .findFirst()
                        .orElse("UNASSIGNED");

        String branchCode =
                user.getBranch() == null
                        ? null
                        : user.getBranch().getCode();

        String branch =
                user.getBranch() == null
                        ? "Head Office"
                        : user.getBranch().getName()
                          + " - "
                          + user.getBranch().getCode();

        return new PortalPrincipal(
                user.getId(),
                user.getEmail(),
                user.getEmail(),
                user.getFullName(),
                primaryRole,
                roles,
                permissions,
                branchCode,
                branch
        );
    }
}
