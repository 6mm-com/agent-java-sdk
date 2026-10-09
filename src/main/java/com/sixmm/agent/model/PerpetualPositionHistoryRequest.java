package com.sixmm.agent.model;

// 公开玩家列表可选；省略查询 Agent 直属范围，传入时仅查询该批玩家。
public final class PerpetualPositionHistoryRequest {
    public java.util.List<String> platformUserIds;
    public String symbol;
    public String positionSide;
    public Long startTime;
    public Long endTime;
    public Integer limit;
    public String cursor;
}
