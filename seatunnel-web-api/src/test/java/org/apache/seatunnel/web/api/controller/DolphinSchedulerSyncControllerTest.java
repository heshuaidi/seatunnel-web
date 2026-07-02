package org.apache.seatunnel.web.api.controller;

import org.apache.seatunnel.web.api.scheduler.SchedulerProperties;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerifier;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DolphinSchedulerSyncControllerTest {

    private static final String TOKEN = "seatunnel-lab-token";

    private SyncRunCoordinatorService syncRunCoordinatorService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        syncRunCoordinatorService = Mockito.mock(SyncRunCoordinatorService.class);

        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(new DolphinSchedulerSyncController(
                syncRunCoordinatorService,
                new SchedulerTokenVerifier(properties)
        )).build();
    }

    @Test
    void runShouldReturnDolphinSchedulerFieldsAndPassIdempotencyKey() throws Exception {
        Mockito.when(syncRunCoordinatorService.runTask(eq("oracle_to_starrocks_inline"), any()))
                .thenReturn(runResult());

        mockMvc.perform(post("/api/v1/dolphinscheduler/sync/tasks/oracle_to_starrocks_inline/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerType": "DOLPHINSCHEDULER",
                                  "runMode": "SCHEDULE",
                                  "version": "latest",
                                  "bizDate": "2026-07-02",
                                  "idempotencyKey": "ds-process-instance-10001",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extractRunId").value("run_10086"))
                .andExpect(jsonPath("$.data.batchId").value("INLINE_20260702_000001"))
                .andExpect(jsonPath("$.data.taskCode").value("oracle_to_starrocks_inline"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.seatunnelJobId").value("job-1"));

        ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
        Mockito.verify(syncRunCoordinatorService).runTask(eq("oracle_to_starrocks_inline"), captor.capture());
        RunTaskRequest request = captor.getValue();
        assertEquals(SyncTriggerType.SCHEDULED.getCode(), request.getTriggerType());
        assertEquals(SyncRunMode.NORMAL.getCode(), request.getRunMode());
        assertEquals("ds-process-instance-10001", request.getSchedulerRunId());
        assertEquals("ds-process-instance-10001", request.getIdempotencyKey());
        assertEquals("2026-07-02", request.getParams().get("biz_date"));
        assertFalse(request.getWaitForFinish());
    }

    @Test
    void runShouldAcceptAuthorizationBearerToken() throws Exception {
        Mockito.when(syncRunCoordinatorService.runTask(eq("oracle_to_starrocks_inline"), any()))
                .thenReturn(runResult());

        mockMvc.perform(post("/api/v1/dolphinscheduler/sync/tasks/oracle_to_starrocks_inline/run")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void runShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/dolphinscheduler/sync/tasks/oracle_to_starrocks_inline/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("scheduler token invalid"));

        Mockito.verifyNoInteractions(syncRunCoordinatorService);
    }

    @Test
    void getRunShouldMapDetailStatusAndFields() throws Exception {
        Mockito.when(syncRunCoordinatorService.getRun("run_10086")).thenReturn(runDetail());

        mockMvc.perform(get("/api/v1/dolphinscheduler/sync/runs/run_10086")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extractRunId").value("run_10086"))
                .andExpect(jsonPath("$.data.batchId").value("INLINE_20260702_000001"))
                .andExpect(jsonPath("$.data.taskCode").value("oracle_to_starrocks_inline"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.seatunnelJobId").value("job-1"))
                .andExpect(jsonPath("$.data.startTime").value("2026-07-02 01:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("2026-07-02 01:03:20"));
    }

    @Test
    void getRunShouldMapCheckFailedToFailed() throws Exception {
        RunDetailVO detail = runDetail();
        detail.setRunStatus(SyncRunStatus.CHECK_FAILED.getCode());
        detail.setErrorMessage("sink count mismatch");
        Mockito.when(syncRunCoordinatorService.getRun("run_10086")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/dolphinscheduler/sync/runs/run_10086")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("sink count mismatch"));
    }

    private RunResultVO runResult() {
        RunResultVO result = new RunResultVO();
        result.setRunId("run_10086");
        result.setBatchId("INLINE_20260702_000001");
        result.setTaskCode("oracle_to_starrocks_inline");
        result.setRunStatus(SyncRunStatus.SUBMITTED.getCode());
        result.setSeatunnelJobId("job-1");
        return result;
    }

    private RunDetailVO runDetail() {
        RunDetailVO detail = new RunDetailVO();
        detail.setRunId("run_10086");
        detail.setBatchId("INLINE_20260702_000001");
        detail.setTaskCode("oracle_to_starrocks_inline");
        detail.setRunStatus(SyncRunStatus.SUCCESS.getCode());
        detail.setSeatunnelJobId("job-1");
        detail.setStartTime("2026-07-02 01:00:00");
        detail.setEndTime("2026-07-02 01:03:20");
        return detail;
    }
}
