package com.sixmm.agent.model;

// 每次请求只读取一页；成交玩家只接受平台公开用户 ID。
public final class PerpetualTradesRequest {
    public java.util.List<String> platformUserIds;
    public String symbol;
    public String orderId;
    public Long startTime;
    public Long endTime;
    public Integer limit;
    public String cursor;
}
