package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

class HoconRenderServiceImplTest {

    private final HoconRenderServiceImpl service = new HoconRenderServiceImpl();

    @Test
    void renderShouldReplaceVariables() {
        String rendered = service.render(
                "source { query = \"select * from t where id > ${start_id} and id <= ${end_id}\" }",
                Map.of("start_id", 10, "end_id", 20)
        );

        Assertions.assertEquals(
                "source { query = \"select * from t where id > 10 and id <= 20\" }",
                rendered
        );
    }

    @Test
    void renderShouldRejectMissingVariables() {
        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.render("env { job.name = \"${run_id}\" }", Map.of())
        );

        Assertions.assertTrue(exception.getMessage().contains("run_id"));
    }

    @Test
    void renderShouldFormatDateTimeVariables() {
        String rendered = service.render(
                "where update_time < '${batch_end_time}'",
                Map.of("batch_end_time", LocalDateTime.of(2026, 6, 2, 3, 4, 5))
        );

        Assertions.assertEquals("where update_time < '2026-06-02 03:04:05'", rendered);
    }

    @Test
    void renderShouldSupportCheckSqlVariables() {
        String rendered = service.render(
                "select count(*) from t where batch_id = '${batch_id}' and update_time >= '${batch_start_time}'",
                Map.of(
                        "batch_id", "batch-1",
                        "batch_start_time", LocalDateTime.of(2026, 6, 1, 0, 0, 0)
                )
        );

        Assertions.assertEquals(
                "select count(*) from t where batch_id = 'batch-1' and update_time >= '2026-06-01 00:00:00'",
                rendered
        );
    }

    @Test
    void extractVariablesShouldReturnUniqueVariablesInOrder() {
        Set<String> variables = service.extractVariables(
                "source { user='${source_username}' password='${source_password}' where='id <= ${batch_end_value}' again='${source_username}' }"
        );

        Assertions.assertEquals(
                List.of("source_username", "source_password", "batch_end_value"),
                List.copyOf(variables)
        );
    }

    @Test
    void findMissingVariablesShouldReturnAllMissingVariables() {
        List<String> missingVariables = service.findMissingVariables(
                "source { user='${source_username}' password='${source_password}' end='${batch_end_value}' }",
                Map.of("source_username", "st_lab")
        );

        Assertions.assertEquals(List.of("source_password", "batch_end_value"), missingVariables);
    }

    @Test
    void findMissingVariablesShouldOnlyReturnVariableNamesForSensitiveValues() {
        List<String> missingVariables = service.findMissingVariables(
                "source { password='${source_password}' token='${source_token}' }",
                Map.of("source_password", "plain-password")
        );

        Assertions.assertEquals(List.of("source_token"), missingVariables);
        Assertions.assertFalse(missingVariables.toString().contains("plain-password"));
    }
}
