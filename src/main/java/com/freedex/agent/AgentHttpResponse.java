package com.freedex.agent;

public final class AgentHttpResponse {
    private final int statusCode;
    private final String body;

    public AgentHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
