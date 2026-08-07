package com.sixmm.agent.model;

import java.util.ArrayList;
import java.util.List;

public class QueryExchangeRatesResponse extends AgentResponse {
    public String snapshotVersion;
    public String provider;
    public String sourceDate;
    public long fetchedAt;
    public long expiresAt;
    public String pricingPolicy;
    public String usdtUsdRate;
    public String rateType;
    public String usage;
    public String rateMeaning;
    public List<ExchangeRateItem> rates = new ArrayList<>();
}
