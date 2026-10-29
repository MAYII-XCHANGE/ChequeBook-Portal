package com.example.chequebookrequestportal.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VendorWorkflowProperties {
    private final boolean finacleRegistrationEnabled;

    public VendorWorkflowProperties(
            @Value("${app.vendor.finacle-registration-enabled:false}") boolean finacleRegistrationEnabled) {
        this.finacleRegistrationEnabled = finacleRegistrationEnabled;
    }

    public boolean finacleRegistrationEnabled() {
        return finacleRegistrationEnabled;
    }
}
