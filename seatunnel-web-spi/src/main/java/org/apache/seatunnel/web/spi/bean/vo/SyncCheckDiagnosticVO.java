package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SyncCheckDiagnosticVO {

    private Integer checkCount;

    private Integer enabledCheckCount;

    private List<String> missingDatasourceIds;

    private Boolean renderable;

    private List<String> checkSqlPreview;

    private List<CheckItemVO> checks;

    private Map<String, Object> maskedParams;

    @Data
    public static class CheckItemVO {

        private String checkCode;

        private String checkName;

        private String checkType;

        private Long datasourceId;

        private Boolean enabled;

        private Boolean renderable;

        private List<String> missingVariables;

        private String renderedSqlPreview;

        private Boolean executeSql;

        private Boolean executed;

        private String actualValue;

        private Boolean passed;

        private String errorMessage;
    }
}
