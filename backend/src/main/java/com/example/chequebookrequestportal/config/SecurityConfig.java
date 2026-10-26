package com.example.chequebookrequestportal.config;

import com.example.chequebookrequestportal.auth.LoginRateLimitFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final Environment environment;

    public SecurityConfig(
            JwtTokenFilter jwtTokenFilter,
            LoginRateLimitFilter loginRateLimitFilter,
            Environment environment) {

        this.jwtTokenFilter = jwtTokenFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(request -> {

                    request.requestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/mfa/entrust/verify",
                            "/api/v1/auth/refresh-token",
                            "/api/v1/auth/logout",
                            "/actuator/health"
                    ).permitAll();

                    if (isDevelopmentProfile()) {

                        request.requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }

                    request.anyRequest().authenticated();
                })

                .exceptionHandling(handler ->
                        handler
                                .authenticationEntryPoint(
                                        (request, response, exception) ->
                                                writeSecurityError(
                                                        response,
                                                        HttpStatus.UNAUTHORIZED,
                                                        "UNAUTHENTICATED",
                                                        "Authentication is required."
                                                )
                                )

                                .accessDeniedHandler(
                                        (request, response, exception) ->
                                                writeSecurityError(
                                                        response,
                                                        HttpStatus.FORBIDDEN,
                                                        "FORBIDDEN",
                                                        "The request was denied by the security policy."
                                                )
                                )
                )

                .addFilterBefore(
                        jwtTokenFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        loginRateLimitFilter,
                        JwtTokenFilter.class
                )

                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    FilterRegistrationBean<LoginRateLimitFilter> disableLoginRateLimitServletRegistration(
            LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<JwtTokenFilter> disableJwtTokenServletRegistration(
            JwtTokenFilter filter) {
        FilterRegistrationBean<JwtTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private boolean isDevelopmentProfile() {

        return Stream.concat(
                        Arrays.stream(
                                environment.getActiveProfiles()
                        ),
                        Arrays.stream(
                                environment.getDefaultProfiles()
                        )
                )
                .anyMatch(
                        profile ->
                                "local".equals(profile)
                                        || "dev".equals(profile)
                );
    }

    private static void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws java.io.IOException {

        response.setStatus(status.value());
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType(
                "application/json"
        );

        response.getWriter().write(
                "{\"code\":\""
                        + code
                        + "\",\"message\":\""
                        + message
                        + "\",\"fieldErrors\":{},\"retryable\":false}"
        );
    }

}
