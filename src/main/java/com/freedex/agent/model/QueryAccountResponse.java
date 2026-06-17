package com.freedex.agent.model;

import java.util.ArrayList;
import java.util.List;

public class QueryAccountResponse extends AgentResponse {
    public String agentCode;
    public String agentStatus;
    public List<AssetInfo> assets = new ArrayList<>();
}
