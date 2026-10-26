package com.example.chequebookrequestportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ChequeBookServiceTest {
    @Mock
    private RestTemplate restTemplate;

    @Test
    void sendsAcknowledgementObtainedFlagAsY() {
        String url = "http://finacle.example/chequebooks";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.<HttpEntity<?>>any(),
                eq(String.class))).thenReturn(ResponseEntity.ok(successResponse()));
        ChequeBookService service = new ChequeBookService(url, "N", restTemplate);

        service.requestChequeBook("0123456789", 50, "1", "001000");

        ArgumentCaptor<HttpEntity<?>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), entity.capture(), eq(String.class));
        assertThat(entity.getValue().getBody())
                .asString()
                .contains("<AckObtainedFlg>Y</AckObtainedFlg>")
                .contains("<BeginChqNum>001000</BeginChqNum>");
    }

    private String successResponse() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FIXML xmlns="http://www.finacle.com/fixml">
                  <Body>
                    <ChkbkAddResponse>
                      <HostTransaction>
                        <Id>HOST-1</Id>
                        <Status>SUCCESS</Status>
                      </HostTransaction>
                    </ChkbkAddResponse>
                  </Body>
                </FIXML>
                """;
    }
}
