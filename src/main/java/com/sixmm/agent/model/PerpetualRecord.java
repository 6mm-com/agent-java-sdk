package com.sixmm.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 只提供公开用户 ID，不提供内部 userId、Agent 或站点标识。
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PerpetualRecord {
    public String entityType;
    public String entityId;
    public String platformUserId;
    public PerpetualData data;
}
