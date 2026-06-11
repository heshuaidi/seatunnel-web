package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

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
}
