package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.example.chequebookrequestportal.config.RestClientConfig.sslRestTemplate;

@Service
public class AdAuthenticationClient {
    private static final Logger log = LoggerFactory.getLogger(AdAuthenticationClient.class);

    private final String authorizationUrl;
    private final RestTemplate client;
    private final ObjectMapper mapper;

    public AdAuthenticationClient(
            @Value("${ad.authorization-url}") String authorizationUrl,
            RestTemplate client,
            ObjectMapper mapper) {
        this.authorizationUrl = authorizationUrl;
        this.client = client;
        this.mapper = mapper;
    }

    public boolean authenticate(String username, String password) {
        if (authorizationUrl.isBlank()) {
            throw unavailable("The AD authentication endpoint is not configured.");
        }
        Map<String, String> payload = Map.of(
                "user", username.toLowerCase(Locale.ROOT),
                "pass", Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
        try {
            RestTemplate sslRestTemplate = sslRestTemplate();
            String body = sslRestTemplate.postForObject(authorizationUrl, payload, String.class);
            if (body == null || body.isBlank()) return false;
            JsonNode status = mapper.readTree(body).path("status");
            return status.asBoolean(false) || "true".equalsIgnoreCase(status.asText());
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                log.warn("AD authentication endpoint returned status {}", exception.getStatusCode());
                throw unavailable("The AD authentication service is unavailable.");
            }
            return false;
        } catch (ResourceAccessException exception) {
            log.warn("Could not connect securely to the AD authentication endpoint", exception);
            throw unavailable("The AD authentication service is unavailable.");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Could not process the AD authentication response", exception);
            throw unavailable("The AD authentication response could not be processed.");
        }
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "AD_UNAVAILABLE", message, true);
    }
}
