package com.sixmm.agent.model;

// 公开玩家列表可选；省略查询 Agent 直属范围，条件委托仍必填列表。
public final class CurrentPerpetualOrdersRequest {
    public String orderCategory;
    public java.util.List<String> platformUserIds;
    public String symbol;
    public Integer limit;
    public String cursor;
}
