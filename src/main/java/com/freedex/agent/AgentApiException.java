package com.freedex.agent;

public class AgentApiException extends AgentSdkException {
    private final int httpStatus;
    private final int code;
    private final String responseBody;

    public AgentApiException(String message, int httpStatus, int code, String responseBody) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.responseBody = responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
