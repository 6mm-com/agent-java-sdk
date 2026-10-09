package com.sixmm.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 永续六类只读查询统一响应。
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PerpetualResponse extends AgentResponse {
    public PerpetualPage data;
}
