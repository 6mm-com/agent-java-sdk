package com.sixmm.agent.model;

// 平台 Funding 余额，不包含合作商本地钱包或平台内部账户标识。
public class FundingBalance {
    public String assetCode;
    public String availableAmount;
    public String heldAmount;
    public String totalAmount;
    public boolean materialized;
}
