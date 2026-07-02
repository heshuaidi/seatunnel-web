package org.apache.seatunnel.web.api.controller;

import org.apache.seatunnel.web.api.scheduler.SchedulerExecutorService;
import org.apache.seatunnel.web.api.scheduler.SchedulerProperties;
import org.apache.seatunnel.web.api.scheduler.SchedulerRunFailureException;
import org.apache.seatunnel.web.api.scheduler.SchedulerRunResponse;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerExecutorControllerTest {

    private static final String TOKEN = "seatunnel-lab-token";

    private SchedulerExecutorService schedulerExecutorService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        schedulerExecutorService = Mockito.mock(SchedulerExecutorService.class);
        mockMvc = buildMockMvc(true, TOKEN);
    }

    @Test
    void runShouldRejectMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("scheduler token missing"));

        Mockito.verifyNoInteractions(schedulerExecutorService);
    }

    @Test
    void runShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("scheduler token invalid"));

        Mockito.verifyNoInteractions(schedulerExecutorService);
    }

    @Test
    void runShouldRejectWhenSchedulerApiDisabled() throws Exception {
        mockMvc = buildMockMvc(true, "");

        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("scheduler api disabled"));

        Mockito.verifyNoInteractions(schedulerExecutorService);
    }

    @Test
    void runShouldCallExecutorServiceWhenTokenValid() throws Exception {
        Mockito.when(schedulerExecutorService.run(eq(220L), any()))
                .thenReturn(response(220L, 330L, "SUBMITTED"));

        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobDefineId").value(220))
                .andExpect(jsonPath("$.data.jobInstanceId").value(330))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        Mockito.verify(schedulerExecutorService).run(eq(220L), any());
    }

    @Test
    void getRunShouldCallExecutorServiceWhenTokenValid() throws Exception {
        Mockito.when(schedulerExecutorService.run(eq(220L), any()))
                .thenReturn(response(220L, 330L, "SUBMITTED"));

        mockMvc.perform(get("/api/v1/scheduler/job-defines/220/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobInstanceId").value(330));
    }

    @Test
    void runShouldRejectInvalidJobDefineId() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/job-defines/not-a-number/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("request parameter jobDefineId is not valid"));

        Mockito.verifyNoInteractions(schedulerExecutorService);
    }

    @Test
    void runShouldRejectMissingJobDefineId() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/job-defines/run")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("jobDefineId is required"));

        Mockito.verifyNoInteractions(schedulerExecutorService);
    }

    @Test
    void runSyncShouldReturnOkWhenFinished() throws Exception {
        Mockito.when(schedulerExecutorService.runSync(eq(220L), any()))
                .thenReturn(response(220L, 330L, "FINISHED"));

        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run-sync")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\",\"timeoutSeconds\":600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"));
    }

    @Test
    void runSyncShouldReturnServerErrorWhenJobFailed() throws Exception {
        SchedulerRunResponse response = response(220L, 330L, "FAILED");
        Mockito.when(schedulerExecutorService.runSync(eq(220L), any()))
                .thenThrow(new SchedulerRunFailureException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "finished with status FAILED",
                        response));

        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run-sync")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void runSyncShouldReturnGatewayTimeoutWhenWaitTimeout() throws Exception {
        SchedulerRunResponse response = response(220L, 330L, "TIMEOUT");
        Mockito.when(schedulerExecutorService.runSync(eq(220L), any()))
                .thenThrow(new SchedulerRunFailureException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "wait timeout after 600 seconds",
                        response));

        mockMvc.perform(post("/api/v1/scheduler/job-defines/220/run-sync")
                        .header(SchedulerTokenVerifier.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggeredBy\":\"DOLPHINSCHEDULER\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.data.status").value("TIMEOUT"));
    }

    private MockMvc buildMockMvc(boolean enabled, String configuredToken) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(enabled);
        properties.setToken(configuredToken);
        properties.setOperator("scheduler");

        SchedulerExecutorController controller = new SchedulerExecutorController(
                schedulerExecutorService,
                new SchedulerTokenVerifier(properties));
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private SchedulerRunResponse response(Long jobDefineId, Long jobInstanceId, String status) {
        SchedulerRunResponse response = new SchedulerRunResponse();
        response.setJobDefineId(jobDefineId);
        response.setJobInstanceId(jobInstanceId);
        response.setStatus(status);
        response.setTriggeredBy("DOLPHINSCHEDULER");
        response.setOperator("scheduler");
        return response;
    }
}
