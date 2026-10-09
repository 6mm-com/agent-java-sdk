package com.sixmm.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 分页不保证跨页原子快照；空页也应根据 hasMore 决定是否继续。
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PerpetualPage {
    public java.util.List<PerpetualRecord> items;
    public String nextCursor;
    public boolean hasMore;
}
