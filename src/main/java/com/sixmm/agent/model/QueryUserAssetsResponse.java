package com.sixmm.agent.model;

public class QueryUserAssetsResponse extends AgentResponse {
    public String environment;

    public String platformUserId;
    public FundingBalance funding;
    public String walletBalance;
    public String frozenMargin;
    public String usedMargin;
    public String availableBalance;
    public String isolatedMargin;
    public long version;
    public boolean isSimulatedUser;
}
