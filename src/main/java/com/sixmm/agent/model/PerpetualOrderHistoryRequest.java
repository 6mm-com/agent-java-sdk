package com.sixmm.agent.model;

// 公开玩家列表可选；省略查询 Agent 直属范围，条件委托仍必填列表。
public final class PerpetualOrderHistoryRequest {
    public String orderCategory;
    public java.util.List<String> platformUserIds;
    public String symbol;
    public String orderId;
    public String conditionId;
    public String clientOrderId;
    public String side;
    public String status;
    public Long startTime;
    public Long endTime;
    public Integer limit;
    public String cursor;
}
