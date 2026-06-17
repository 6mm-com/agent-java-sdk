package com.sixmm.agent.model;

public class TransferAllOutRequest {
    public String agentOrderNo;
    public String agentUserId;
    public String platformUserId;
    public String currency;

    public static TransferAllOutRequest of(String agentOrderNo, String agentUserId, String currency) {
        TransferAllOutRequest request = new TransferAllOutRequest();
        request.agentOrderNo = agentOrderNo;
        request.agentUserId = agentUserId;
        request.currency = currency;
        return request;
    }

    public static TransferAllOutRequest byPlatformUserId(String agentOrderNo, String platformUserId, String currency) {
        TransferAllOutRequest request = new TransferAllOutRequest();
        request.agentOrderNo = agentOrderNo;
        request.platformUserId = platformUserId;
        request.currency = currency;
        return request;
    }

    public TransferAllOutRequest withPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
        return this;
    }
}
