package com.example.chequebookrequestportal.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
    public static final String PROFILES = "profiles";
    public static final String USERS = "users";
    public static final String ROLES = "roles";
    public static final String BRANCHES = "branches";
    public static final String ACCOUNT_ELIGIBILITY = "accountEligibility";
    public static final String CHEQUE_REQUESTS = "chequeRequests";
    public static final String CHEQUE_RANGES = "chequeRanges";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                PROFILES,
                USERS,
                ROLES,
                BRANCHES,
                ACCOUNT_ELIGIBILITY,
                CHEQUE_REQUESTS,
                CHEQUE_RANGES
        );
    }
}
