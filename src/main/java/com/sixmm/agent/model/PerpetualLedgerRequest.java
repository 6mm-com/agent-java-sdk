package com.sixmm.agent.model;

// PG 账变可按公开玩家列表筛选，省略查询 Agent 范围；时间采用记录 createdAt 毫秒。
public final class PerpetualLedgerRequest {
    public java.util.List<String> platformUserIds;
    public String assetType;
    public String changeType;
    public Long startTime;
    public Long endTime;
    public Integer limit;
    public String cursor;
}
