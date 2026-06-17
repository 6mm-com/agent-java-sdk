package com.freedex.agent.model;

public class QueryUserAssetsResponse extends AgentResponse {
    public String platformUserId;
    public String walletBalance;
    public String frozenMargin;
    public String usedMargin;
    public String availableBalance;
    public String isolatedMargin;
    public long version;
    public boolean isSimulatedUser;
}
