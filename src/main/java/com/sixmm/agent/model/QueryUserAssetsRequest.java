package com.sixmm.agent.model;

public class QueryUserAssetsRequest {
    public String platformUserId;

    public static QueryUserAssetsRequest of(String platformUserId) {
        QueryUserAssetsRequest request = new QueryUserAssetsRequest();
        request.platformUserId = platformUserId;
        return request;
    }
}
