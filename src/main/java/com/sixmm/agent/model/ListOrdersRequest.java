package com.sixmm.agent.model;

public class ListOrdersRequest {
    public String orderType;
    public String status;
    public String startTime;
    public String endTime;
    public long page = 1;
    public long pageSize = 20;

    public static ListOrdersRequest page(long page, long pageSize) {
        ListOrdersRequest request = new ListOrdersRequest();
        request.page = page;
        request.pageSize = pageSize;
        return request;
    }
}
