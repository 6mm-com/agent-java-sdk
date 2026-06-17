package com.freedex.agent.model;

public class BindRequest {
    public String agentUserId;
    public String ext;

    public static BindRequest of(String agentUserId) {
        BindRequest request = new BindRequest();
        request.agentUserId = agentUserId;
        return request;
    }

    public BindRequest withExt(String ext) {
        this.ext = ext;
        return this;
    }
}
