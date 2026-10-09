package com.sixmm.agent.model;

// 公开玩家列表可选；省略查询 Agent 直属范围，传入时仅查询该批玩家。
public final class CurrentPerpetualPositionsRequest {
    public java.util.List<String> platformUserIds;
    public String symbol;
    public Integer limit;
    public String cursor;
}
