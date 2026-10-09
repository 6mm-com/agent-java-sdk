package com.sixmm.agent.model;

public class QueryUserAssetsRequest {
    // 省略时兼容正式环境；只有显式 SIMULATED 才切换。
    public String environment;

    public QueryUserAssetsRequest withEnvironment(String value) {
        this.environment = ExecutionEnvironment.require(value);
        return this;
    }

    public String platformUserId;
    public Boolean includeFunding;

    public QueryUserAssetsRequest withFunding() {
        this.includeFunding = true;
        return this;
    }

    public static QueryUserAssetsRequest of(String platformUserId) {
        QueryUserAssetsRequest request = new QueryUserAssetsRequest();
        request.platformUserId = platformUserId;
        return request;
    }
}
