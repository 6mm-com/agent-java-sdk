package com.sixmm.agent.model;

import java.util.ArrayList;
import java.util.List;

public class ListSupportedFiatCurrenciesResponse extends AgentResponse {
    public String targetCurrency;
    public List<String> sourceCurrencies = new ArrayList<>();
}
