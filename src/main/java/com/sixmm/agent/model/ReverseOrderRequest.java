package com.sixmm.agent.model;

public class ReverseOrderRequest {
    // 省略时兼容正式环境；只有显式 SIMULATED 才切换。
    public String environment;

    public ReverseOrderRequest withEnvironment(String value) {
        this.environment = ExecutionEnvironment.require(value);
        return this;
    }

    public String origOrderNo;
    public String reverseOrderNo;
    public String reverseReason;

    public static ReverseOrderRequest of(String origOrderNo, String reverseOrderNo, String reverseReason) {
        ReverseOrderRequest request = new ReverseOrderRequest();
        request.origOrderNo = origOrderNo;
        request.reverseOrderNo = reverseOrderNo;
        request.reverseReason = reverseReason;
        return request;
    }
}
