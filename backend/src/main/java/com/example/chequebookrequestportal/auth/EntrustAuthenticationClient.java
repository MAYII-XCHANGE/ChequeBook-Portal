package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.common.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

@Component
public class EntrustAuthenticationClient {
    static final String KEY_HEADER = "X-Entrust-Key";
    private static final Logger LOG = LoggerFactory.getLogger(EntrustAuthenticationClient.class);
    private static final String VALIDATE_PATH = "/api/entrust/validate-token";
    private final RestTemplate restTemplate;
    private final String endpointUrl;
    private final String apiKey;
    private final String userGroup;

    public EntrustAuthenticationClient(RestTemplate restTemplate,
            @Value("${entrust.base-url:}") String baseUrl,
            @Value("${entrust.api-key:}") String apiKey,
            @Value("${entrust.user-group:EXTERNAL}") String userGroup) {
        this.restTemplate = restTemplate;
        this.endpointUrl = baseUrl.replaceAll("/+$", "") + VALIDATE_PATH;
        this.apiKey = apiKey;
        this.userGroup = userGroup;
    }

    public boolean validate(String username, String token) {
        if (apiKey.isBlank() || endpointUrl.equals(VALIDATE_PATH)) {
            throw unavailable("The Entrust authentication service is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(KEY_HEADER, apiKey);
        HttpEntity<EntrustValidationRequest> request = new HttpEntity<>(
                new EntrustValidationRequest(username, token, userGroup), headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    endpointUrl, HttpMethod.POST, request, JsonNode.class);
            return explicitlySuccessful(response.getBody());
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw unavailable("The Entrust authentication service is temporarily unavailable.");
            }
            return false;
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            LOG.warn("Entrust authentication request failed: {}", exception.getClass().getSimpleName());
            throw unavailable("The Entrust authentication service is temporarily unavailable.");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.warn("Could not process the Entrust authentication response: {}",
                    exception.getClass().getSimpleName());
            throw unavailable("The Entrust authentication response could not be processed.");
        }
    }

    private boolean explicitlySuccessful(JsonNode body) {
        if (body == null || body.isNull()) {
            return false;
        }

        if (hasSuccessfulStatus(body)) {
            return true;
        }

        JsonNode data = body.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (hasSuccessfulStatus(item)) {
                    return true;
                }
            }
        } else if (hasSuccessfulStatus(data)) {
            return true;
        }

        LOG.warn("Entrust response did not contain a recognized explicit success field.");
        return false;
    }

    private boolean hasSuccessfulStatus(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        for (String field : List.of("status", "success", "valid")) {
            if (truthy(node.path(field))) {
                return true;
            }
        }
        return false;
    }

    private boolean truthy(JsonNode value) {
        return value.isBoolean() ? value.booleanValue() : "true".equalsIgnoreCase(value.asText());
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "ENTRUST_UNAVAILABLE", message, true);
    }

    private record EntrustValidationRequest(String username, String token, String userGroup) {
    }
}
