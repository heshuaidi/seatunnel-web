package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.engine.client.rest.SeaTunnelRestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

class SyncZetaClientImplTest {

    @Test
    void submitJobTextFailureShouldExtractDeepestRootCauseFromResponseBody() {
        SyncZetaClientImpl service = new SyncZetaClientImpl();
        SeaTunnelRestClient restClient = Mockito.mock(SeaTunnelRestClient.class);
        ReflectionTestUtils.setField(service, "seaTunnelRestClient", restClient);

        String responseBody = """
                {
                  "status": "fail",
                  "message": "org.apache.seatunnel.engine.common.exception.JobException: submit failed Caused by: org.apache.seatunnel.connectors.seatunnel.starrocks.exception.StarRocksConnectorException: Writing records to StarRocks failed Caused by: java.sql.SQLException: Access denied for user 'st_lab' at com.mysql.cj.jdbc.ConnectionImpl.createNewIO(ConnectionImpl.java:825)"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                headers,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        Mockito.when(restClient.submitJobText(
                        Mockito.eq(1L),
                        Mockito.anyString(),
                        Mockito.eq("hocon"),
                        Mockito.isNull(),
                        Mockito.eq("job-1"),
                        Mockito.eq(false)
                ))
                .thenThrow(exception);

        ServiceException thrown = Assertions.assertThrows(
                ServiceException.class,
                () -> service.submitJob(1L, "job-1", "env {}")
        );

        Assertions.assertTrue(thrown.getMessage().contains("POST /submit-job(text) failed:"));
        Assertions.assertTrue(thrown.getMessage().contains("Access denied for user 'st_lab'"));
        Assertions.assertFalse("POST /submit-job(text) failed".equals(thrown.getMessage()));
    }
}
