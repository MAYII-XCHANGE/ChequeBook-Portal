package com.example.chequebookrequestportal.corebanking.client;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.dto.request.CreditTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.NameEnquiryRequest;
import com.example.chequebookrequestportal.corebanking.dto.response.CreditTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
public class CoreBankingClientImpl implements CoreBankingClient {
    private static final Logger log = LoggerFactory.getLogger(CoreBankingClientImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String baseUrl;
    private final String username;
    private final String password;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CoreBankingClientImpl(
            @Value("${core-banking.base-url}") String baseUrl,
            @Value("${core-banking.username:}") String username,
            @Value("${core-banking.password:}") String password,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.username = username;
        this.password = password;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public NameEnquiryResponse nameEnquiry(NameEnquiryRequest request) {
        Map<String, String> payload = Map.of(
                "tran_ref", numericReference(30),
                "acct_no", request.accountNumber());
        return post("/nameenquiry", payload, NameEnquiryResponse.class, "name enquiry");
    }

    @Override
    public CreditTransferResponse creditTransfer(CreditTransferRequest request) {
        return post("/credittransfer", request, CreditTransferResponse.class, "credit transfer");
    }

    private <T> T post(String path, Object request, Class<T> responseType, String operation) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!username.isBlank() || !password.isBlank())
            headers.setBasicAuth(username, password);

        try {
            log.info("Sending core-banking {} request", request);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), String.class);
            log.info("response ................ {}", response);
            if (!response.getStatusCode().is2xxSuccessful())
                throw unavailable("CORE_BANKING_HTTP_ERROR", "Core banking rejected the " + operation + " request.");
            if (response.getBody() == null || response.getBody().isBlank())
                throw unavailable("CORE_BANKING_EMPTY_RESPONSE", "Core banking returned an empty response.");
            return objectMapper.readValue(response.getBody(), responseType);
        }
        catch (ApiException exception) {
            throw exception;
        }
        catch (ResourceAccessException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "CORE_BANKING_TIMEOUT",
                    "Core banking did not respond in time.", true);
        }
        catch (RestClientException exception) {
            throw unavailable("CORE_BANKING_UNAVAILABLE", "Core banking is temporarily unavailable.");
        }
        catch (Exception exception) {
            throw unavailable("CORE_BANKING_INVALID_RESPONSE", "Core banking returned an invalid response.");
        }
    }

    private ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message, true);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("core-banking.base-url must be configured");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String numericReference(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++)
            value.append(RANDOM.nextInt(10));
        return value.toString();
    }
}
