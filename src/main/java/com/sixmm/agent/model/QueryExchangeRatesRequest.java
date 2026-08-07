package com.sixmm.agent.model;

import java.util.Locale;

public class QueryExchangeRatesRequest {
    public String sourceCurrencies;

    public static QueryExchangeRatesRequest of(String... sourceCurrencies) {
        QueryExchangeRatesRequest request = new QueryExchangeRatesRequest();
        if (sourceCurrencies == null || sourceCurrencies.length == 0) {
            return request;
        }

        StringBuilder value = new StringBuilder();
        for (String sourceCurrency : sourceCurrencies) {
            if (sourceCurrency == null) {
                continue;
            }
            String normalized = sourceCurrency.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(normalized);
        }
        request.sourceCurrencies = value.toString();
        return request;
    }
}
