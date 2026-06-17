package com.sixmm.agent.model;

public class QueryOrderRequest {
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
