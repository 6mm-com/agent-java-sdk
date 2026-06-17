package com.sixmm.agent.model;

import java.util.ArrayList;
import java.util.List;

public class ListOrdersResponse extends AgentResponse {
    public long total;
    public long page;
    public long pageSize;
    public List<OrderSummary> orders = new ArrayList<>();
}
