package com.sixmm.agent.model;

public class WalletTransferQueryRequest {
    // 省略时兼容正式环境；只有显式 SIMULATED 才切换。
    public String environment;

    public WalletTransferQueryRequest withEnvironment(String value) {
        this.environment = ExecutionEnvironment.require(value);
        return this;
    }

    public String agentOrderNo;

    public static WalletTransferQueryRequest of(String orderNo) {
        WalletTransferQueryRequest request = new WalletTransferQueryRequest();
        request.agentOrderNo = orderNo;
        return request;
    }
}
