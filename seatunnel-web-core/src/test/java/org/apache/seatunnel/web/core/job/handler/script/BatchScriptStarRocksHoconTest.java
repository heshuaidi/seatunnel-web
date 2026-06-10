package org.apache.seatunnel.web.core.job.handler.script;

import org.apache.seatunnel.web.core.job.model.JobDefinitionAnalysisResult;
import org.apache.seatunnel.web.spi.bean.dto.batch.BatchScriptJobSaveCommand;
import org.apache.seatunnel.web.spi.bean.dto.config.ScriptJobContent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchScriptStarRocksHoconTest {

    @Test
    void validatesBuildsAndAnalyzesCustomStarRocksSourceHocon() throws Exception {
        String hocon = ""
                + "env { parallelism = 1 }\n"
                + "source {\n"
                + "  StarRocks {\n"
                + "    nodeUrls = [\"starrocks-dev:8040\"]\n"
                + "    username = \"st\"\n"
                + "    password = \"St_dev_123456!\"\n"
                + "    database = \"st_test\"\n"
                + "    table_list = [\n"
                + "      { table = \"users\", schema { fields { id = \"bigint\", name = \"string\" } } },\n"
                + "      { table = \"orders\", schema { fields { id = \"bigint\", amount = \"decimal(10,2)\" } } }\n"
                + "    ]\n"
                + "  }\n"
                + "}\n"
                + "sink { Console {} }\n";

        ScriptJobDefinitionHandler handler = new ScriptJobDefinitionHandler();
        injectParser(handler);

        BatchScriptJobSaveCommand command = new BatchScriptJobSaveCommand();
        ScriptJobContent content = new ScriptJobContent();
        content.setScriptType("HOCON");
        content.setHoconContent(hocon);
        command.setContent(content);

        assertDoesNotThrow(() -> handler.validate(command));
        assertEquals(hocon, handler.buildHoconConfig(command));

        JobDefinitionAnalysisResult analysis = handler.analyze(command);
        assertEquals("StarRocks", analysis.getSourceType());
        assertTrue(analysis.getSourceTable().contains("users"));
        assertTrue(analysis.getSourceTable().contains("orders"));
    }

    private void injectParser(ScriptJobDefinitionHandler handler) throws Exception {
        Field field = ScriptJobDefinitionHandler.class.getDeclaredField("scriptJobDefinitionParser");
        field.setAccessible(true);
        field.set(handler, new ScriptJobDefinitionParser());
    }
}
