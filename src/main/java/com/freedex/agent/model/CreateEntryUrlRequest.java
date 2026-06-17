package com.freedex.agent.model;

public class CreateEntryUrlRequest {
    public String agentUserId;
    public String redirectPath;
    public String returnUrl;

    public static CreateEntryUrlRequest of(String agentUserId) {
        CreateEntryUrlRequest request = new CreateEntryUrlRequest();
        request.agentUserId = agentUserId;
        return request;
    }

    public CreateEntryUrlRequest withRedirectPath(String redirectPath) {
        this.redirectPath = redirectPath;
        return this;
    }

    public CreateEntryUrlRequest withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }
}
