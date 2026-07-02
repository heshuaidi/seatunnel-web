package org.apache.seatunnel.web.api.dolphinscheduler;

import lombok.Data;

@Data
public class DolphinSchedulerApiResponse<T> {

    private boolean success;

    private T data;

    private String message;

    public static <T> DolphinSchedulerApiResponse<T> success(T data) {
        DolphinSchedulerApiResponse<T> response = new DolphinSchedulerApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> DolphinSchedulerApiResponse<T> failure(String message) {
        DolphinSchedulerApiResponse<T> response = new DolphinSchedulerApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
