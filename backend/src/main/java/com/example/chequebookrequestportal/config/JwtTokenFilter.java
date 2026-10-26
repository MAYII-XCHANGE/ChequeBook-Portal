package com.example.chequebookrequestportal.config;

import com.example.chequebookrequestportal.auth.JwtTokenService;
import com.example.chequebookrequestportal.auth.JwtTokenService.TokenType;
import com.example.chequebookrequestportal.auth.UserDetailsServiceImpl;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final StaffUserRepository users;

    public JwtTokenFilter(JwtTokenService jwtTokenService, UserDetailsServiceImpl userDetailsService,
            StaffUserRepository users) {
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwtTokenService.username(token);
            StaffUser user = username == null ? null : users.findByEmailIgnoreCase(username).orElse(null);
            if (user != null && user.isActive()
                    && (user.getBranch() == null || user.getBranch().isActive())
                    && jwtTokenService.isValid(token, user.getTokenVersion(), TokenType.ACCESS)) {
                var userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
