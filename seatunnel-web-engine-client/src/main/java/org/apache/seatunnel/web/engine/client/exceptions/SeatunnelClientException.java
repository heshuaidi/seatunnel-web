package org.apache.seatunnel.web.engine.client.exceptions;



public class SeatunnelClientException extends RuntimeException {
    private final int httpStatus;
    private final String responseBody;
    private final String responseHeaders;

    public SeatunnelClientException(String message, int httpStatus, String responseBody, Throwable cause) {
        this(message, httpStatus, responseBody, null, cause);
    }

    public SeatunnelClientException(
            String message,
            int httpStatus,
            String responseBody,
            String responseHeaders,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getResponseBody() { return responseBody; }
    public String getResponseHeaders() { return responseHeaders; }
}
