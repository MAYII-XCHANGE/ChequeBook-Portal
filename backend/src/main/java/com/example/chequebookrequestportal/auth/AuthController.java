package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.auth.dto.AuthResponse;
import com.example.chequebookrequestportal.auth.dto.EntrustVerificationRequest;
import com.example.chequebookrequestportal.auth.dto.LoginRequest;
import com.example.chequebookrequestportal.auth.dto.MfaChallengeResponse;
import com.example.chequebookrequestportal.common.ApiResponse;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import com.example.chequebookrequestportal.security.SecurityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final SecurityService security;

    public AuthController(AuthService auth, SecurityService security) {
        this.auth = auth;
        this.security = security;
    }

//     @PostMapping("/login")
//     ApiResponse<MfaChallengeResponse> login(@Valid @RequestBody LoginRequest
//     request) {
//     return new ApiResponse<>("MFA_REQUIRED", "Entrust verification is required.",
//     auth.login(request));
//     }

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful.", auth.login(request));
    }

    @PostMapping("/mfa/entrust/verify")
    ApiResponse<AuthResponse> verifyEntrust(@Valid @RequestBody EntrustVerificationRequest request) {
        return ApiResponse.ok("Login successful.", auth.verifyEntrust(request));
    }

    @PostMapping("/refresh-token")
    ApiResponse<AuthResponse> refresh(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ApiResponse.ok("Token refreshed.", auth.refresh(bearer(authorization)));
    }

    @GetMapping("/profile")
    ApiResponse<PortalPrincipal> profile() {
        PortalPrincipal principal = security.principal();
        return ApiResponse.ok("Profile fetched.", auth.profile(principal.email()));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(Authentication authentication,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authentication != null && authentication.getPrincipal() instanceof PortalPrincipal principal) {
            auth.logout(principal.email());
        } else {
            auth.logoutUsingRefreshToken(bearer(authorization));
        }
        return ApiResponse.ok("Logout successful.", null);
    }

    private String bearer(String authorization) {
        if (authorization == null || authorization.isBlank())
            return null;
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

}
