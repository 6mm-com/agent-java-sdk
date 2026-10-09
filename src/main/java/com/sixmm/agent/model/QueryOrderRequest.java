package com.sixmm.agent.model;

public class QueryOrderRequest {
    // 省略时兼容正式环境；只有显式 SIMULATED 才切换。
    public String environment;

    public QueryOrderRequest withEnvironment(String value) {
        this.environment = ExecutionEnvironment.require(value);
        return this;
    }

    public String orderNo;
    public String orderType;

    public static QueryOrderRequest of(String orderNo, OrderQueryType orderType) {
        QueryOrderRequest request = new QueryOrderRequest();
        request.orderNo = orderNo;
        request.orderType = orderType.name();
        return request;
    }

    public static QueryOrderRequest of(String orderNo, String orderType) {
        QueryOrderRequest request = new QueryOrderRequest();
        request.orderNo = orderNo;
        request.orderType = orderType;
        return request;
    }
}
