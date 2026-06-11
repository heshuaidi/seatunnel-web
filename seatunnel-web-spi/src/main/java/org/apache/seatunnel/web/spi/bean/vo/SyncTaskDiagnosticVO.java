package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SyncTaskDiagnosticVO {

    private TaskInfoVO task;

    private VersionInfoVO version;

    private IncrementalConfigInfoVO incrementalConfig;

    private WatermarkInfoVO watermark;

    private SyncRangePreviewVO rangePreview;

    private SyncHoconDiagnosticVO hocon;

    private SyncCheckDiagnosticVO checks;

    private ClientInfoVO client;

    private DiagnosticsVO diagnostics;

    @Data
    public static class TaskInfoVO {

        private Long taskId;

        private String taskCode;

        private String taskName;

        private String status;

        private String taskType;

        private String sourceType;

        private String sinkType;

        private String engineType;

        private Long clientId;

        private Boolean incrementalEnabled;

        private String incrementalStrategy;
    }

    @Data
    public static class VersionInfoVO {

        private Long versionId;

        private Integer versionNo;

        private String publishStatus;

        private String hoconHash;

        private Boolean exists;
    }

    @Data
    public static class IncrementalConfigInfoVO {

        private Boolean exists;

        private String strategy;

        private String watermarkField;

        private String watermarkFieldType;

        private String startValue;

        private Integer lookbackSeconds;

        private Integer maxBatchSeconds;
    }

    @Data
    public static class WatermarkInfoVO {

        private Boolean exists;

        private String watermarkKey;

        private String currentValue;

        private String previousValue;

        private String currentValueType;

        private Long lastSuccessRunId;

        private String lastSuccessBatchId;
    }

    @Data
    public static class ClientInfoVO {

        private Long clientId;

        private Boolean exists;

        private String warning;
    }

    @Data
    public static class DiagnosticsVO {

        private String level;

        private List<String> messages;
    }
}
