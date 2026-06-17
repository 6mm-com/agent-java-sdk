package com.sixmm.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixmm.agent.model.AgentResponse;
import com.sixmm.agent.model.BindRequest;
import com.sixmm.agent.model.BindResponse;
import com.sixmm.agent.model.CreateEmbedTokenRequest;
import com.sixmm.agent.model.CreateEmbedTokenResponse;
import com.sixmm.agent.model.CreateEntryUrlRequest;
import com.sixmm.agent.model.CreateEntryUrlResponse;
import com.sixmm.agent.model.ListOrdersRequest;
import com.sixmm.agent.model.ListOrdersResponse;
import com.sixmm.agent.model.QueryAccountRequest;
import com.sixmm.agent.model.QueryAccountResponse;
import com.sixmm.agent.model.QueryOrderRequest;
import com.sixmm.agent.model.QueryOrderResponse;
import com.sixmm.agent.model.QueryUserAssetsRequest;
import com.sixmm.agent.model.QueryUserAssetsResponse;
import com.sixmm.agent.model.ReverseOrderRequest;
import com.sixmm.agent.model.ReverseOrderResponse;
import com.sixmm.agent.model.TransferAllOutRequest;
import com.sixmm.agent.model.TransferAllOutResponse;
import com.sixmm.agent.model.TransferRequest;
import com.sixmm.agent.model.TransferResponse;
import com.sixmm.agent.model.VersionResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<LinkedHashMap<String, Object>>() {
    };

    private final AgentClientConfig config;

    public AgentClient(AgentClientConfig config) {
        if (config == null) {
            throw new AgentSdkException("config is required");
        }
        this.config = config;
    }

    public BindResponse bind(BindRequest request) {
        return postSigned("/v1/agent/bind", request, BindResponse.class);
    }

    public TransferResponse transfer(TransferRequest request) {
        return postSigned("/v1/agent/transfer", request, TransferResponse.class);
    }

    public TransferAllOutResponse transferAllOut(TransferAllOutRequest request) {
        return postSigned("/v1/agent/transfer-all-out", request, TransferAllOutResponse.class);
    }

    public ReverseOrderResponse reverse(ReverseOrderRequest request) {
        return postSigned("/v1/agent/reverse", request, ReverseOrderResponse.class);
    }

    public QueryOrderResponse queryOrder(QueryOrderRequest request) {
        return postSigned("/v1/agent/query-order", request, QueryOrderResponse.class);
    }

    public ListOrdersResponse listOrders(ListOrdersRequest request) {
        return postSigned("/v1/agent/list-orders", request, ListOrdersResponse.class);
    }

    public QueryAccountResponse queryAccount(QueryAccountRequest request) {
        return postSigned("/v1/agent/query-account", request, QueryAccountResponse.class);
    }

    public QueryUserAssetsResponse queryUserAssets(QueryUserAssetsRequest request) {
        return postSigned("/v1/agent/query-user-assets", request, QueryUserAssetsResponse.class);
    }

    public CreateEntryUrlResponse createEntryUrl(CreateEntryUrlRequest request) {
        return postSigned("/v1/agent/create-entry-url", request, CreateEntryUrlResponse.class);
    }

    public CreateEmbedTokenResponse createEmbedToken(CreateEmbedTokenRequest request) {
        return postSigned("/v1/agent/create-embed-token", request, CreateEmbedTokenResponse.class);
    }

    public VersionResponse version() {
        return send("GET", "/version", Collections.<String, String>emptyMap(), null, VersionResponse.class);
    }

    private <T> T postSigned(String path, Object request, Class<T> responseType) {
        LinkedHashMap<String, Object> body = requestToMap(request);
        scrubEmptyValues(body);
        body.put("agentCode", config.getAgentCode());
        body.put("timestamp", Instant.now(config.getClock()).getEpochSecond());
        body.put("nonce", config.getNonceGenerator().newNonce());
        applyDefaultCurrency(path, body);
        body.put("sign", AgentSigner.signParams(body, config.getApiSecret()));

        byte[] json = toJson(body);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        return send("POST", path, headers, json, responseType);
    }

    private <T> T send(String method, String path, Map<String, String> headers, byte[] body, Class<T> responseType) {
        AgentHttpResponse response;
        try {
            response = config.getHttpTransport().send(method, config.getBaseUrl() + path, headers, body, config.getTimeout());
        } catch (IOException e) {
            throw new AgentSdkException("agent api request failed", e);
        }

        String responseBody = response.getBody();
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new AgentApiException("agent api returned http status " + response.getStatusCode(),
                    response.getStatusCode(), 0, responseBody);
        }

        T result = fromJson(responseBody, responseType);
        if (result instanceof AgentResponse) {
            AgentResponse agentResponse = (AgentResponse) result;
            if (agentResponse.getCode() != 0) {
                throw new AgentApiException(agentResponse.getMessage() + " (code=" + agentResponse.getCode() + ")",
                        response.getStatusCode(), agentResponse.getCode(), responseBody);
            }
        }
        return result;
    }

    private LinkedHashMap<String, Object> requestToMap(Object request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }
        return MAPPER.convertValue(request, MAP_TYPE);
    }

    private void applyDefaultCurrency(String path, Map<String, Object> body) {
        if (AgentClientConfig.trimToEmpty(config.getDefaultCurrency()).isEmpty()) {
            return;
        }
        if (!path.equals("/v1/agent/transfer")
                && !path.equals("/v1/agent/transfer-all-out")
                && !path.equals("/v1/agent/query-account")) {
            return;
        }
        Object currency = body.get("currency");
        if (currency == null || AgentClientConfig.trimToEmpty(String.valueOf(currency)).isEmpty()) {
            body.put("currency", config.getDefaultCurrency());
        }
    }

    private void scrubEmptyValues(Map<String, Object> body) {
        Iterator<Map.Entry<String, Object>> it = body.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            Object value = entry.getValue();
            if (value == null) {
                it.remove();
                continue;
            }
            if (value instanceof String && ((String) value).trim().isEmpty()) {
                it.remove();
            }
        }
    }

    private byte[] toJson(Object body) {
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new AgentSdkException("failed to serialize agent api request", e);
        }
    }

    private <T> T fromJson(String body, Class<T> responseType) {
        try {
            return MAPPER.readValue(body, responseType);
        } catch (Exception e) {
            throw new AgentSdkException("failed to parse agent api response", e);
        }
    }
}
