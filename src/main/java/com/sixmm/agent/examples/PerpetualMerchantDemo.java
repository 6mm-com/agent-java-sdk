package com.sixmm.agent.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixmm.agent.AgentClient;
import com.sixmm.agent.AgentClientConfig;
import com.sixmm.agent.AgentHttpResponse;
import com.sixmm.agent.AgentSdkException;
import com.sixmm.agent.model.*;
import java.nio.file.Files;
import java.nio.file.Paths;

// 商户后端的一页只读示例；默认离线，不放入普通玩家 Portal。
public final class PerpetualMerchantDemo {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PerpetualMerchantDemo() { }

    public static void main(String[] args) throws Exception {
        String resource = args.length > 0 ? args[0] : "orders/current";
        Class<?> requestClass = requestClass(resource);
        boolean live = "1".equals(System.getenv("AGENT_DEMO_LIVE"));
        if (live && args.length < 2) {
            throw new AgentSdkException("live mode requires an explicit request JSON file");
        }
        String json = args.length > 1 ? new String(Files.readAllBytes(Paths.get(args[1])), java.nio.charset.StandardCharsets.UTF_8)
                : resource.equals("trades") ? "{\"platformUserIds\":[\"1000000042\",\"1000000043\"],\"limit\":2}"
                : resource.equals("orders/current") ? "{\"orderCategory\":\"NORMAL\",\"limit\":2}"
                : resource.equals("orders/history") ? "{\"orderCategory\":\"CONDITIONAL\",\"platformUserIds\":[\"1000000042\",\"1000000043\"],\"limit\":2}" : "{\"limit\":2}";
        com.fasterxml.jackson.databind.JsonNode input = MAPPER.readTree(json);
        if (input == null || !input.isObject()) { throw new AgentSdkException("request file must contain a JSON object"); }
        // CLI 先校验原始 JSON 类型，防止数值被 Jackson 转成公开 ID 字符串或 null 被省略。
        if (input.has("platformUserIds")) {
            com.fasterxml.jackson.databind.JsonNode ids = input.get("platformUserIds");
            if (!ids.isArray()) { throw new AgentSdkException("platformUserIds must be a JSON array"); }
            for (com.fasterxml.jackson.databind.JsonNode id : ids) {
                if (!id.isTextual()) { throw new AgentSdkException("platformUserIds must contain strings"); }
            }
        }
        Object request = MAPPER.treeToValue(input, requestClass);
        AgentClientConfig.Builder config = AgentClientConfig.builder()
                .baseUrl(live ? requiredEnv("AGENT_BASE_URL") : "https://offline.invalid")
                .agentCode(live ? requiredEnv("AGENT_CODE") : "offline-agent")
                .apiSecret(live ? requiredEnv("AGENT_API_SECRET") : "offline-secret");
        if (!live) {
            config.httpTransport((method, url, headers, body, timeout) -> new AgentHttpResponse(200,
                    resource.equals("account/ledger") ? "{\"code\":0,\"message\":\"offline fixture\",\"data\":{\"items\":[{\"entityType\":\"ACCOUNT_LEDGER\",\"entityId\":\"9001\",\"platformUserId\":\"1000000042\",\"data\":{\"ledgerId\":\"7001\",\"symbol\":\"BTCUSDT\",\"assetType\":\"USDT\",\"changeType\":\"handling_fee\",\"amount\":\"-0.000000000000000001\",\"walletBalanceBefore\":\"10.000000000000000001\",\"walletBalanceAfter\":\"10\",\"frozenBalanceBefore\":\"0\",\"frozenBalanceAfter\":\"0\",\"referenceId\":\"8001\",\"createdAt\":1799913600123}}],\"nextCursor\":\"\",\"hasMore\":false}}" :
                    "{\"code\":0,\"message\":\"offline fixture\",\"data\":{\"items\":[],\"nextCursor\":\"\",\"hasMore\":false}}"));
        }
        AgentClient client = new AgentClient(config.build());
        PerpetualResponse response;
        switch (resource) {
            case "orders/current": response = client.listCurrentPerpetualOrders((CurrentPerpetualOrdersRequest) request); break;
            case "orders/history": response = client.listPerpetualOrderHistory((PerpetualOrderHistoryRequest) request); break;
            case "trades": response = client.listPerpetualTrades((PerpetualTradesRequest) request); break;
            case "positions/current": response = client.listCurrentPerpetualPositions((CurrentPerpetualPositionsRequest) request); break;
            case "positions/history": response = client.listPerpetualPositionHistory((PerpetualPositionHistoryRequest) request); break;
            case "account/ledger": response = client.listPerpetualLedger((PerpetualLedgerRequest) request); break;
            default: throw new AgentSdkException("unsupported read resource");
        }
        System.out.println(live ? "Live read: one page, no automatic retry" : "Offline fixture: no network request");
        com.fasterxml.jackson.databind.JsonNode output = MAPPER.valueToTree(response);
        if (resource.equals("account/ledger")) {
            for (com.fasterxml.jackson.databind.JsonNode record : output.path("data").path("items")) {
                com.fasterxml.jackson.databind.node.ObjectNode data = (com.fasterxml.jackson.databind.node.ObjectNode) record.path("data");
                data.retain(java.util.Arrays.asList("ledgerId", "symbol", "assetType", "changeType", "amount",
                        "walletBalanceBefore", "walletBalanceAfter", "frozenBalanceBefore", "frozenBalanceAfter", "referenceId", "createdAt"));
                java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> values = data.elements();
                while (values.hasNext()) { if (values.next().isNull()) { values.remove(); } }
            }
        }
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    private static Class<?> requestClass(String resource) {
        switch (resource) {
            case "orders/current": return CurrentPerpetualOrdersRequest.class;
            case "orders/history": return PerpetualOrderHistoryRequest.class;
            case "trades": return PerpetualTradesRequest.class;
            case "positions/current": return CurrentPerpetualPositionsRequest.class;
            case "positions/history": return PerpetualPositionHistoryRequest.class;
            case "account/ledger": return PerpetualLedgerRequest.class;
            default: throw new AgentSdkException("unsupported read resource");
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) { throw new AgentSdkException(name + " is required in live mode"); }
        return value;
    }
}
