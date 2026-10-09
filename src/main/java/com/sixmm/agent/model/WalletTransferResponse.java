package com.sixmm.agent.model;

public class WalletTransferResponse extends AgentResponse {
    public String environment;

    public String orderNo;
    public String status;
    public String direction;
    public String currency;
    public String amount;
    public boolean transferAll;
    public String agentUserId;
    public String platformUserId;
    public String resultCode;
}
