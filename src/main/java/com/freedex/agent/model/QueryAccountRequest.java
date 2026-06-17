package com.freedex.agent.model;

public class QueryAccountRequest {
    public String currency;

    public static QueryAccountRequest of(String currency) {
        QueryAccountRequest request = new QueryAccountRequest();
        request.currency = currency;
        return request;
    }
}
