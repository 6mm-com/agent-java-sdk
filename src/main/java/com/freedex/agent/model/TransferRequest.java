package com.freedex.agent.model;

import java.math.BigDecimal;

public class TransferRequest {
    public String agentOrderNo;
    public String agentUserId;
    public String platformUserId;
    public Direction direction;
    public String currency;
    public String amount;

    public static TransferRequest fixed(String agentOrderNo, String agentUserId, Direction direction, String currency, String amount) {
        TransferRequest request = new TransferRequest();
        request.agentOrderNo = agentOrderNo;
        request.agentUserId = agentUserId;
        request.direction = direction;
        request.currency = currency;
        request.amount = amount;
        return request;
    }

    public static TransferRequest fixed(String agentOrderNo, String agentUserId, Direction direction, String currency, BigDecimal amount) {
        return fixed(agentOrderNo, agentUserId, direction, currency, amount.toPlainString());
    }

    public static TransferRequest fixedByPlatformUserId(String agentOrderNo, String platformUserId, Direction direction, String currency, String amount) {
        TransferRequest request = new TransferRequest();
        request.agentOrderNo = agentOrderNo;
        request.platformUserId = platformUserId;
        request.direction = direction;
        request.currency = currency;
        request.amount = amount;
        return request;
    }

    public static TransferRequest fixedByPlatformUserId(String agentOrderNo, String platformUserId, Direction direction, String currency, BigDecimal amount) {
        return fixedByPlatformUserId(agentOrderNo, platformUserId, direction, currency, amount.toPlainString());
    }

    public TransferRequest withPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
        return this;
    }
}
