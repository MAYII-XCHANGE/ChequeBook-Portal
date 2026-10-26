package com.example.chequebookrequestportal.service;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.request.ChequeBookRequest;
import com.example.chequebookrequestportal.request.ChequeBookResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.example.chequebookrequestportal.config.RestClientConfig.sslRestTemplate;

@Service
public class ChequeBookService {
    private static final Logger log = LoggerFactory.getLogger(ChequeBookService.class);
    private static final String ACKNOWLEDGEMENT_OBTAINED_FLAG = "Y";
    private static final DateTimeFormatter FINACLE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final String finacleUrl;
    private final String inventoryRequired;
    private final RestTemplate restTemplate;

    public ChequeBookService(
            @Value("${finacle.url}") String finacleUrl,
            @Value("${app.chequebook.inventory-required}") String inventoryRequired,
            RestTemplate restTemplate) {
        if (finacleUrl == null || finacleUrl.isBlank())
            throw new IllegalArgumentException("finacle.url must be configured");
        this.finacleUrl = finacleUrl;
        this.inventoryRequired = inventoryRequired;
        this.restTemplate = restTemplate;
    }

    public ChequeBookResponse requestChequeBook(String accountNumber, int leafCount, String branchCode,
            String beginningChequeNumber) {
        if (beginningChequeNumber == null || !beginningChequeNumber.matches("\\d{1,30}"))
            throw new IllegalArgumentException("beginningChequeNumber must contain 1 to 30 digits");
        ChequeBookRequest request = new ChequeBookRequest(
                leafCount,
                ACKNOWLEDGEMENT_OBTAINED_FLAG,
                accountNumber,
                inventoryRequired,
                leafCount,
                beginningChequeNumber,
                normalizeSolId(branchCode));

        String requestUuid = UUID.randomUUID().toString();
        String globalUuid = UUID.randomUUID().toString();
        String messageDateTime = LocalDateTime.now().format(FINACLE_DATE_TIME);
        String xmlRequest = buildXmlRequest(request, requestUuid, globalUuid, messageDateTime);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        try {
            log.info("Submitting Finacle cheque book request for account ending {}",
                    maskedAccountSuffix(accountNumber));
            log.info("FI xmlRequest..to.url: {}.....with payload..... {}", finacleUrl, xmlRequest);
            RestTemplate client = finacleUrl.startsWith("https://") ? sslRestTemplate() : restTemplate;
            ResponseEntity<String> response = client.exchange(
                    finacleUrl, HttpMethod.POST, new HttpEntity<>(xmlRequest, headers), String.class);
            log.info("FI Response............. {}", response);
            if (!response.getStatusCode().is2xxSuccessful())
                throw unavailable("CHEQUEBOOK_HTTP_ERROR", "Finacle rejected the cheque book request.");
            if (response.getBody() == null || response.getBody().isBlank())
                throw unavailable("CHEQUEBOOK_EMPTY_RESPONSE", "Finacle returned an empty cheque book response.");

            ChequeBookResponse parsed = parseResponse(response.getBody());
            log.info("Finacle cheque book request completed with host status {}",
                    parsed.data() == null ? "UNKNOWN" : parsed.data().hostTransactionStatus());
            return parsed;
        }
        catch (ResourceAccessException exception) {
            log.info("Finacle ResourceAccessException while calling {}", finacleUrl, exception);

            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "CHEQUEBOOK_TIMEOUT",
                    "Finacle did not respond to the cheque book request in time.",
                    true
            );
        }
        catch (RestClientException exception) {
            log.info("Finacle RestClientException while calling {}", finacleUrl, exception);

            throw unavailable(
                    "CHEQUEBOOK_UNAVAILABLE",
                    "The Finacle cheque book service is temporarily unavailable."
            );
        } catch (Exception e) {
            log.info("ee {}", e);
            throw new RuntimeException(e);
        }
    }

    private String buildXmlRequest(ChequeBookRequest request, String requestUuid, String globalUuid,
            String messageDateTime) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FIXML xmlns="http://www.finacle.com/fixml"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://www.finacle.com/fixml ChkbkAdd.xsd">
                  <Header>
                    <RequestHeader>
                      <MessageKey>
                        <GlobalUUID>%s</GlobalUUID>
                        <RequestUUID>%s</RequestUUID>
                        <ServiceId>ChkbkAdd</ServiceId>
                        <ServiceRequestVersion>11.13</ServiceRequestVersion>
                        <ChannelId>COR</ChannelId>
                        <AccessToken>null</AccessToken>
                        <ClientIP>null</ClientIP>
                        <OriginatorInstanceId/>
                        <OriginatorVersion/>
                        <LanguageId/>
                      </MessageKey>
                      <RequestMessageInfo>
                        <BankId>01</BankId>
                        <TimeZone/>
                        <EntityId/>
                        <EntityType/>
                        <ArmCorrelationId/>
                        <MessageDateTime>%s</MessageDateTime>
                        <ExceptionOverrideFlag/>
                        <SrvInvocmodeOverrideFlag/>
                      </RequestMessageInfo>
                      <Security>
                        <Token><PasswordToken><UserId/><Password/></PasswordToken></Token>
                        <FICertToken/>
                        <RealUserLoginSessionId/>
                        <RealUser/>
                        <RealUserPwd/>
                        <SSOTransferToken/>
                      </Security>
                    </RequestHeader>
                  </Header>
                  <Body>
                    <ChkbkAddRequest>
                      <ChkbkAddRq>
                        <LeavesPerBk>%d</LeavesPerBk>
                        <AckObtainedFlg>%s</AckObtainedFlg>
                        <AcctId>%s</AcctId>
                        <InvtReqd>%s</InvtReqd>
                        <TotNumOfLeaves>%d</TotNumOfLeaves>
                        <BeginChqNum>%s</BeginChqNum>
                        <SolId>%s</SolId>
                      </ChkbkAddRq>
                    </ChkbkAddRequest>
                  </Body>
                </FIXML>
                """.formatted(
                        escapeXml(globalUuid),
                        escapeXml(requestUuid),
                        escapeXml(messageDateTime),
                        request.leavesPerBook(),
                        escapeXml(request.acknowledgementObtainedFlag()),
                        escapeXml(request.accountId()),
                        escapeXml(request.inventoryRequired()),
                        request.totalNumberOfLeaves(),
                        escapeXml(request.beginningChequeNumber()),
                        escapeXml(request.solId()));
    }

    private ChequeBookResponse parseResponse(String xmlResponse) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");

        Document document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));
        ChequeBookResponse.Data data = new ChequeBookResponse.Data(
                tagValue(document, "GlobalUUID"),
                tagValue(document, "RequestUUID"),
                tagValue(document, "ServiceRequestId"),
                tagValue(document, "ServiceId"),
                tagValue(document, "ServiceRequestVersion"),
                tagValue(document, "ServiceVersion"),
                tagValue(document, "ChannelId"),
                tagValue(document, "OriginatorId"),
                tagValue(document, "BankId"),
                tagValue(document, "TimeZone"),
                tagValue(document, "MessageDateTime"),
                childTagValue(document, "UBUSTransaction", "Id"),
                childTagValue(document, "UBUSTransaction", "Status"),
                childTagValue(document, "HostTransaction", "Id"),
                childTagValue(document, "HostTransaction", "Status"),
                childTagValue(document, "HostParentTransaction", "Id"),
                childTagValue(document, "HostParentTransaction", "Status"),
                tagValue(document, "ErrorCode"),
                firstNonBlank(tagValue(document, "ErrorDesc"), tagValue(document, "ErrorDescription")));

        boolean successful = "SUCCESS".equalsIgnoreCase(data.hostTransactionStatus());
        String message = successful
                ? "Cheque book request submitted successfully"
                : firstNonBlank(data.errorDescription(), "Cheque book request failed.");
        return new ChequeBookResponse(successful, message, data);
    }

    private String tagValue(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() == 0 ? null : normalizedText(nodes.item(0));
    }

    private String childTagValue(Document document, String parentTag, String childTag) {
        NodeList parents = document.getElementsByTagNameNS("*", parentTag);
        if (parents.getLength() == 0 || !(parents.item(0) instanceof Element parent))
            return null;
        NodeList children = parent.getElementsByTagNameNS("*", childTag);
        return children.getLength() == 0 ? null : normalizedText(children.item(0));
    }

    private String normalizedText(Node node) {
        String value = node.getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSolId(String branchCode) {
        if (branchCode == null || branchCode.isBlank())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BRANCH_SOL_ID_REQUIRED",
                    "The initiating branch must have a branch code.");
        String value = branchCode.trim();
        return value.matches("\\d+") && value.length() < 3 ? "0".repeat(3 - value.length()) + value : value;
    }

    private String escapeXml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String maskedAccountSuffix(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank())
            return "UNKNOWN";
        return accountNumber.length() <= 4 ? accountNumber : accountNumber.substring(accountNumber.length() - 4);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message, true);
    }
}
