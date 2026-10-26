package com.example.chequebookrequestportal.security;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {
    private final StaffUserRepository users;

    public SecurityService(StaffUserRepository users) {
        this.users = users;
    }

    public PortalPrincipal principal() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (p instanceof PortalPrincipal pp) {
            StaffUser user = users.findById(pp.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                    "USER_NOT_FOUND", "Authenticated user profile no longer exists."));
            if (!user.isActive())
                throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "This portal account is disabled.");
            if (user.getBranch() != null && !user.getBranch().isActive())
                throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_DISABLED", "This user's branch is disabled.");
            return pp;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }

    public StaffUser user() {
        return users.findById(principal().id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "USER_NOT_FOUND", "Authenticated user profile no longer exists."));
    }

    public String actor() {
        return principal().name();
    }

    public void requirePermission(String permission) {
        if (!principal().permissions().contains(permission))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action.");
    }

    public void requireAnyPermission(String... permissions) {
        for (String permission : permissions)
            if (principal().permissions().contains(permission)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized to perform this action.");
    }
}
