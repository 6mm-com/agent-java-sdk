package com.freedex.agent.model;

public class CreateEmbedTokenRequest {
    public String agentUserId;
    public String channelId;
    public String symbol;

    public static CreateEmbedTokenRequest of(String agentUserId, String channelId) {
        CreateEmbedTokenRequest request = new CreateEmbedTokenRequest();
        request.agentUserId = agentUserId;
        request.channelId = channelId;
        return request;
    }

    public CreateEmbedTokenRequest withSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }
}
