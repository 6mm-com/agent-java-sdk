package com.sixmm.agent.model;

public class QueryOrderResponse extends AgentResponse {
    public String orderType;
    public String orderNo;
    public String status;
    public String direction;
    public String transferMode;
    public String currency;
    public String amount;
    public String agentUserId;
    public String platformUserId;
    public String failReason;
    public String createdAt;
    public String completedAt;
}
