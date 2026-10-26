package com.example.chequebookrequestportal.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record PortalPrincipal(
        Long id,
        String username,
        String email,
        String name,
        String role,
        Set<String> roles,
        Set<String> permissions,
        String branchCode,
        String branch
) implements Serializable, UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        permissions.forEach(
                permission ->
                        authorities.add(
                                new SimpleGrantedAuthority(permission)
                        )
        );

        roles.forEach(
                value ->
                        authorities.add(
                                new SimpleGrantedAuthority(value)
                        )
        );

        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
}
