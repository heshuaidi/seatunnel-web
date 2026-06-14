package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class BatchLinkUpIncrementalContextVO {

    private String batchId;

    private String runId;

    private String taskCode;

    private String batchStartValue;

    private String batchEndValue;

    private String batchStartTime;

    private String batchEndTime;

    private String watermarkValue;

    private String watermarkTime;

    private String lastSuccessBatchId;

    private String lastSuccessRunId;

    private String bizDate;

    private Map<String, Object> customContext;

    private Map<String, Object> variables;

    private List<String> systemVariables = new ArrayList<>();

    private List<SqlExecutionVO> executedSqls = new ArrayList<>();

    private List<String> missingVariables = new ArrayList<>();

    private List<String> diagnostics = new ArrayList<>();

    @Data
    public static class SqlExecutionVO {

        private String name;

        private Long datasourceId;

        private String renderedSql;

        private List<String> columns = new ArrayList<>();

        private List<Map<String, Object>> rows = new ArrayList<>();

        private Object scalarValue;

        private Boolean success;

        private String errorMessage;
    }
}
