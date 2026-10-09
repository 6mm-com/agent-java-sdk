package com.sixmm.agent.model;

public class WalletTransferRequest {
    // 省略时兼容正式环境；只有显式 SIMULATED 才切换。
    public String environment;

    public WalletTransferRequest withEnvironment(String value) {
        this.environment = ExecutionEnvironment.require(value);
        return this;
    }

    public String agentOrderNo;
    public String agentUserId;
    public String platformUserId;
    public String direction;
    public String currency = "USDT";
    public String amount;
    public boolean transferAll;

    public static WalletTransferRequest of(String orderNo, String userId, String direction, String amount, boolean all) {
        WalletTransferRequest request = new WalletTransferRequest();
        request.agentOrderNo = orderNo;
        request.agentUserId = userId;
        request.direction = direction;
        request.amount = amount;
        request.transferAll = all;
        return request;
    }
}
