package com.sixmm.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixmm.agent.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerpetualClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY = "{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"\",\"hasMore\":false}}";

    @Test
    void arraySignatureMatchesAgentAndKeepsOrder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platformUserIds", Arrays.asList("1000000042", "1000000043"));
        assertEquals("platformUserIds=[\"1000000042\",\"1000000043\"]", AgentSigner.buildSignPayload(body));
        assertEquals("d0294195edf1eb77339155073578ebe450547a2d6dcd7b954469c0ea7548d6de", AgentSigner.signParams(body, "test-secret"));
        body.put("platformUserIds", Arrays.asList("1000000043", "1000000042"));
        assertNotEquals("d0294195edf1eb77339155073578ebe450547a2d6dcd7b954469c0ea7548d6de", AgentSigner.signParams(body, "test-secret"));
    }

    @Test
    void completeTradeSignatureMatchesAgentVector() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentCode", "demo");
        body.put("timestamp", 1800000000L);
        body.put("nonce", "batch-query-1");
        body.put("platformUserIds", Arrays.asList("1000000042", "1000000043"));
        body.put("symbol", "BTCUSDT");
        body.put("startTime", 1799913600000L);
        body.put("endTime", 1800000000000L);
        body.put("limit", 500);
        assertEquals("d9fc669c88915245257021787b00a94fe417f0e9ac4edc33ada63fcd0c18269e", AgentSigner.signParams(body, "test-secret"));
    }

    @Test
    void completeCurrentNormalOrderSignatureMatchesAgentVector() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentCode", "demo");
        body.put("timestamp", 1800000000L);
        body.put("nonce", "current-order-1");
        body.put("orderCategory", "NORMAL");
        body.put("platformUserIds", Arrays.asList("1000000042", "1000000043"));
        body.put("symbol", "BTCUSDT");
        body.put("limit", 500);
        assertEquals("6437a32a60e18b8badfdd39e62ed4ca4325494c1f7ff64ec3bcca3629aee3f1f", AgentSigner.signParams(body, "test-secret"));
    }

    @Test
    void conditionalOrderSignaturesMatchAgentVectors() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentCode", "demo");
        body.put("timestamp", 1800000000L);
        body.put("nonce", "current-condition-1");
        body.put("orderCategory", "CONDITIONAL");
        body.put("platformUserIds", Arrays.asList("1000000042", "1000000043"));
        body.put("symbol", "BTCUSDT");
        body.put("limit", 500);
        assertEquals("bd4e1a4ff0631600146cb38d21551242bc77edfdf8cbc9b4fc30bd6938d0977c", AgentSigner.signParams(body, "test-secret"));
        body.put("nonce", "history-condition-1");
        body.put("startTime", 1799913600000L);
        body.put("endTime", 1800000000000L);
        assertEquals("6dbbe99c8c6e2eed1ba9f8ace5165f8208f4b5f13cd41464acdc8a51017b2c32", AgentSigner.signParams(body, "test-secret"));
    }

    @Test
    void sixMethodsOnlyUseExpectedReadRoutesAndFilters() throws Exception {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        CurrentPerpetualOrdersRequest current = new CurrentPerpetualOrdersRequest();
        current.orderCategory = "NORMAL";
        current.platformUserIds = Arrays.asList("1000000042", "1000000043");
        client.listCurrentPerpetualOrders(current);
        checkRoute(transport, "orders/current");
        assertEquals("1000000042", MAPPER.readTree(transport.body).path("platformUserIds").get(0).textValue());
        PerpetualOrderHistoryRequest history = new PerpetualOrderHistoryRequest();
        history.orderCategory = "CONDITIONAL";
        history.platformUserIds = Arrays.asList("1000000042", "1000000043");
        history.conditionId = "9223372036854775807";
        history.startTime = 1713024000000L;
        history.endTime = 1713110400000L;
        client.listPerpetualOrderHistory(history);
        checkRoute(transport, "orders/history");
        assertEquals(1713024000000L, MAPPER.readTree(transport.body).path("startTime").longValue());
        client.listPerpetualTrades(trades());
        checkRoute(transport, "trades");
        JsonNode body = MAPPER.readTree(transport.body);
        assertEquals("1000000042", body.path("platformUserIds").get(0).textValue());
        assertFalse(body.has("userIds"));
        assertFalse(body.has("agentUserIds"));
        client.listCurrentPerpetualPositions(new CurrentPerpetualPositionsRequest());
        checkRoute(transport, "positions/current");
        client.listPerpetualPositionHistory(new PerpetualPositionHistoryRequest());
        checkRoute(transport, "positions/history");
        client.listPerpetualLedger(ledger());
        checkRoute(transport, "account/ledger");
        assertEquals(6, transport.calls);
        assertEquals(2, MAPPER.readTree(transport.body).path("platformUserIds").size());
    }

    @Test
    void invalidTradesNeverReachTransport() {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        PerpetualTradesRequest request = new PerpetualTradesRequest();
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        request.platformUserIds = Collections.emptyList();
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        request.platformUserIds = Arrays.asList("1000000042", "1000000042");
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        request.platformUserIds = Arrays.asList("42");
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        request.platformUserIds = Arrays.asList("0000000042");
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        request.platformUserIds = Collections.nCopies(101, "1000000042");
        assertThrows(AgentSdkException.class, () -> client.listPerpetualTrades(request));
        assertEquals(0, transport.calls);
    }

    @Test
    void currentNormalOrdersRejectInvalidExplicitPlayersBeforeTransport() {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        CurrentPerpetualOrdersRequest request = new CurrentPerpetualOrdersRequest();
        request.orderCategory = "NORMAL";
        for (java.util.List<String> ids : Arrays.asList(Collections.<String>emptyList(),
                Arrays.asList("42"), Arrays.asList("0000000042"), Arrays.asList("1000000042", "1000000042"),
                Collections.nCopies(101, "1000000042"))) {
            request.platformUserIds = ids;
            assertThrows(AgentSdkException.class, () -> client.listCurrentPerpetualOrders(request));
        }
        assertEquals(0, transport.calls);
    }

    @Test
    void currentNormalOrdersKeepPlayerScopeAcrossPagesAndAcceptHundredPlayers() throws Exception {
        Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"current-page-2\",\"hasMore\":true}}");
        AgentClient client = client(transport);
        CurrentPerpetualOrdersRequest request = new CurrentPerpetualOrdersRequest();
        request.orderCategory = "NORMAL";
        request.platformUserIds = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) { request.platformUserIds.add(Long.toString(1000000000L + i)); }
        PerpetualResponse first = client.listCurrentPerpetualOrders(request);
        JsonNode firstBody = MAPPER.readTree(transport.body);
        assertEquals(100, firstBody.path("platformUserIds").size());
        assertFalse(firstBody.has("userIds"));
        assertFalse(firstBody.has("agentUserIds"));
        request.cursor = first.data.nextCursor;
        transport.response = EMPTY;
        client.listCurrentPerpetualOrders(request);
        JsonNode nextBody = MAPPER.readTree(transport.body);
        assertEquals(firstBody.path("platformUserIds"), nextBody.path("platformUserIds"));
        assertEquals("current-page-2", nextBody.path("cursor").textValue());
        assertNotEquals(firstBody.path("nonce"), nextBody.path("nonce"));
        checkRoute(transport, "orders/current");
    }

    @Test
    void conditionalOrdersRequireValidPlayersBeforeTransport() {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        CurrentPerpetualOrdersRequest current = new CurrentPerpetualOrdersRequest();
        current.orderCategory = "CONDITIONAL";
        PerpetualOrderHistoryRequest history = new PerpetualOrderHistoryRequest();
        history.orderCategory = "CONDITIONAL";
        assertThrows(AgentSdkException.class, () -> client.listCurrentPerpetualOrders(current));
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(history));
        for (java.util.List<String> ids : Arrays.asList(Collections.<String>emptyList(),
                Arrays.asList("42"), Arrays.asList("0000000042"), Arrays.asList("1000000042", "1000000042"),
                Collections.nCopies(101, "1000000042"))) {
            current.platformUserIds = ids;
            history.platformUserIds = ids;
            assertThrows(AgentSdkException.class, () -> client.listCurrentPerpetualOrders(current));
            assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(history));
        }
        assertEquals(0, transport.calls);
    }

    @Test
    void conditionalOrdersKeepExplicitPlayerScopeAcrossPages() throws Exception {
        for (boolean historical : Arrays.asList(false, true)) {
            Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"condition-page-2\",\"hasMore\":true}}");
            AgentClient client = client(transport);
            CurrentPerpetualOrdersRequest current = new CurrentPerpetualOrdersRequest();
            current.orderCategory = "CONDITIONAL";
            current.platformUserIds = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) { current.platformUserIds.add(Long.toString(1000000000L + i)); }
            PerpetualOrderHistoryRequest history = new PerpetualOrderHistoryRequest();
            history.orderCategory = "CONDITIONAL";
            history.platformUserIds = current.platformUserIds;
            history.startTime = 1799913600000L;
            history.endTime = 1800000000000L;
            PerpetualResponse first = historical ? client.listPerpetualOrderHistory(history) : client.listCurrentPerpetualOrders(current);
            JsonNode firstBody = MAPPER.readTree(transport.body);
            assertEquals(100, firstBody.path("platformUserIds").size());
            assertFalse(firstBody.has("userIds"));
            assertFalse(firstBody.has("agentUserIds"));
            current.cursor = history.cursor = first.data.nextCursor;
            transport.response = EMPTY;
            if (historical) { client.listPerpetualOrderHistory(history); }
            else { client.listCurrentPerpetualOrders(current); }
            JsonNode nextBody = MAPPER.readTree(transport.body);
            assertEquals(firstBody.path("platformUserIds"), nextBody.path("platformUserIds"));
            assertEquals("condition-page-2", nextBody.path("cursor").textValue());
            assertNotEquals(firstBody.path("nonce"), nextBody.path("nonce"));
            checkRoute(transport, historical ? "orders/history" : "orders/current");
            assertEquals(2, transport.calls);
        }
    }

    @Test
    void optionalPlayerResourcesPreserveBothScopesAndSignaturesAcrossPages() throws Exception {
        for (String resource : Arrays.asList("orders/current", "orders/history", "positions/current", "positions/history", "account/ledger")) {
            for (boolean explicit : Arrays.asList(false, true)) {
                Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"scope-page-2\",\"hasMore\":true}}");
                AgentClient client = client(transport);
                Object request = optionalScopeRequest(resource);
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (int i = 0; i < 100; i++) { ids.add(Long.toString(1000000000L + i)); }
                if (explicit) { request.getClass().getField("platformUserIds").set(request, ids); }
                PerpetualResponse first = callOptional(client, resource, request);
                JsonNode before = MAPPER.readTree(transport.body);
                assertEquals(explicit, before.has("platformUserIds"));
                if (explicit) { assertEquals(MAPPER.valueToTree(ids), before.get("platformUserIds")); }
                assertFalse(before.has("userIds"));
                assertFalse(before.has("agentUserIds"));
                checkRoute(transport, resource);
                request.getClass().getField("cursor").set(request, first.data.nextCursor);
                transport.response = EMPTY;
                callOptional(client, resource, request);
                JsonNode after = MAPPER.readTree(transport.body);
                assertEquals(before.get("platformUserIds"), after.get("platformUserIds"));
                assertEquals("scope-page-2", after.path("cursor").textValue());
                assertNotEquals(before.get("nonce"), after.get("nonce"));
                checkRoute(transport, resource);
                assertEquals(2, transport.calls);
            }
        }
    }

    @Test
    void optionalPlayerResourcesNeverBroadenInvalidOrDeniedExplicitScope() throws Exception {
        for (String resource : Arrays.asList("orders/current", "orders/history", "positions/current", "positions/history", "account/ledger")) {
            Capture transport = new Capture(EMPTY);
            AgentClient client = client(transport);
            Object request = optionalScopeRequest(resource);
            for (java.util.List<?> ids : Arrays.<java.util.List<?>>asList(Collections.emptyList(),
                    Arrays.asList("42"), Arrays.asList("0000000042"), Arrays.asList("1000000042", "1000000042"),
                    Arrays.asList(1000000042L), Arrays.asList((String) null), Collections.nCopies(101, "1000000042"))) {
                request.getClass().getField("platformUserIds").set(request, ids);
                assertThrows(AgentSdkException.class, () -> callOptional(client, resource, request));
            }
            assertEquals(0, transport.calls);
            request.getClass().getField("platformUserIds").set(request, Collections.singletonList("1000000042"));
            transport.response = "{\"code\":102002,\"message\":\"player scope denied\"}";
            assertThrows(AgentApiException.class, () -> callOptional(client, resource, request));
            assertEquals(1, transport.calls);
            assertEquals("1000000042", MAPPER.readTree(transport.body).path("platformUserIds").get(0).textValue());
        }
    }

    private static Object optionalScopeRequest(String resource) {
        if (resource.equals("orders/current")) {
            CurrentPerpetualOrdersRequest request = new CurrentPerpetualOrdersRequest();
            request.orderCategory = "NORMAL";
            return request;
        }
        if (resource.equals("orders/history")) {
            PerpetualOrderHistoryRequest request = new PerpetualOrderHistoryRequest();
            request.orderCategory = "NORMAL";
            return request;
        }
        if (resource.equals("account/ledger")) { return new PerpetualLedgerRequest(); }
        return resource.equals("positions/current") ? new CurrentPerpetualPositionsRequest() : new PerpetualPositionHistoryRequest();
    }

    private static PerpetualResponse callOptional(AgentClient client, String resource, Object request) {
        switch (resource) {
            case "orders/current": return client.listCurrentPerpetualOrders((CurrentPerpetualOrdersRequest) request);
            case "orders/history": return client.listPerpetualOrderHistory((PerpetualOrderHistoryRequest) request);
            case "account/ledger": return client.listPerpetualLedger((PerpetualLedgerRequest) request);
            case "positions/current": return client.listCurrentPerpetualPositions((CurrentPerpetualPositionsRequest) request);
            default: return client.listPerpetualPositionHistory((PerpetualPositionHistoryRequest) request);
        }
    }

    @Test
    void filtersRejectInvalidTimeLimitAndCategory() {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        PerpetualOrderHistoryRequest request = new PerpetualOrderHistoryRequest();
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        request.orderCategory = "NORMAL";
        request.conditionId = "1";
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        request.conditionId = null;
        request.startTime = 10L;
        request.endTime = 10L;
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        request.endTime = 11L;
        request.limit = 1001;
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        request.limit = 500;
        request.orderId = "9223372036854775808";
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        request.orderId = null;
        request.symbol = " ";
        assertThrows(AgentSdkException.class, () -> client.listPerpetualOrderHistory(request));
        assertEquals(0, transport.calls);
    }

    @Test
    void emptyPageCanContinueWithExplicitCursorAndFreshNonce() throws Exception {
        Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"opaque-1\",\"hasMore\":true}}");
        AgentClient client = client(transport);
        PerpetualTradesRequest request = trades();
        request.limit = 1000;
        PerpetualResponse first = client.listPerpetualTrades(request);
        assertTrue(first.data.hasMore);
        assertTrue(first.data.items.isEmpty());
        assertEquals(1, transport.calls);
        request.cursor = first.data.nextCursor;
        transport.response = EMPTY;
        assertFalse(client.listPerpetualTrades(request).data.hasMore);
        JsonNode body = MAPPER.readTree(transport.body);
        assertEquals("opaque-1", body.path("cursor").textValue());
        assertEquals("nonce-2", body.path("nonce").textValue());
        assertEquals(2, transport.calls);
    }

    @Test
    void ledgerRejectsInvalidExplicitPlayersBeforeTransport() {
        Capture transport = new Capture(EMPTY);
        AgentClient client = client(transport);
        PerpetualLedgerRequest request = new PerpetualLedgerRequest();
        for (java.util.List<String> ids : Arrays.asList(Collections.<String>emptyList(),
                Arrays.asList("42"), Arrays.asList("0000000042"), Arrays.asList("1000000042", "1000000042"),
                Collections.nCopies(101, "1000000042"))) {
            request.platformUserIds = ids;
            assertThrows(AgentSdkException.class, () -> client.listPerpetualLedger(request));
        }
        assertEquals(0, transport.calls);
    }

    @Test
    void ledgerPreservesHundredPlayersAndTimeFilterAcrossPages() throws Exception {
        Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"nextCursor\":\"ledger-page-2\",\"hasMore\":true}}");
        AgentClient client = client(transport);
        PerpetualLedgerRequest request = ledger();
        request.platformUserIds = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) { request.platformUserIds.add(Long.toString(1000000000L + i)); }
        request.startTime = 1799913600000L;
        request.endTime = 1800000000000L;
        request.assetType = "USDT";
        request.changeType = "handling_fee";
        PerpetualResponse first = client.listPerpetualLedger(request);
        JsonNode before = MAPPER.readTree(transport.body);
        assertEquals(100, before.path("platformUserIds").size());
        request.cursor = first.data.nextCursor;
        transport.response = EMPTY;
        client.listPerpetualLedger(request);
        JsonNode after = MAPPER.readTree(transport.body);
        for (String field : Arrays.asList("platformUserIds", "startTime", "endTime", "assetType", "changeType")) {
            assertEquals(before.path(field), after.path(field));
        }
        assertEquals("ledger-page-2", after.path("cursor").textValue());
        assertNotEquals(before.path("nonce"), after.path("nonce"));
        assertFalse(after.has("userIds"));
        assertFalse(after.has("agentUserIds"));
        checkRoute(transport, "account/ledger");
    }

    @Test
    void ledgerSignatureMatchesAgentVector() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentCode", "demo");
        body.put("timestamp", 1800000000L);
        body.put("nonce", "ledger-query-1");
        body.put("platformUserIds", Arrays.asList("1000000042", "1000000043"));
        body.put("assetType", "USDT");
        body.put("changeType", "handling_fee");
        body.put("startTime", 1799913600000L);
        body.put("endTime", 1800000000000L);
        body.put("limit", 500);
        assertEquals("45f5ab7d5260c0aa34890794a65ee55cd5048cb4b69d0ace2d0b2b5d22d61541", AgentSigner.signParams(body, "test-secret"));
    }

    @Test
    void pgLedgerPreservesDetailsPrecisionAndMissingFields() throws Exception {
        Capture transport = new Capture("{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[{\"entityType\":\"ACCOUNT_LEDGER\",\"entityId\":\"9223372036854775805\",\"platformUserId\":\"1000000042\",\"userId\":42,\"data\":{\"ledgerId\":\"7001\",\"symbol\":\"BTCUSDT\",\"assetType\":\"USDT\",\"changeType\":\"handling_fee\",\"amount\":\"-0.000000000000000001\",\"walletBalanceBefore\":\"10.000000000000000001\",\"walletBalanceAfter\":\"10\",\"frozenBalanceBefore\":\"0.000000000000000001\",\"frozenBalanceAfter\":\"0\",\"referenceId\":\"8001\",\"createdAt\":1799913600123,\"userId\":42,\"description\":\"internal-event-key\"}},{\"entityType\":\"ACCOUNT_LEDGER\",\"entityId\":\"9223372036854775806\",\"platformUserId\":\"1000000042\",\"userId\":42,\"data\":{\"ledgerId\":\"7001\",\"symbol\":\"BTCUSDT\",\"assetType\":\"USDT\",\"changeType\":\"handling_fee\",\"amount\":\"-0.000000000000000001\",\"walletBalanceBefore\":\"10.000000000000000001\",\"walletBalanceAfter\":\"10\",\"frozenBalanceBefore\":\"0.000000000000000001\",\"frozenBalanceAfter\":\"0\",\"referenceId\":\"8001\",\"createdAt\":1799913600123,\"userId\":42,\"description\":\"internal-event-key\"}},{\"entityType\":\"ACCOUNT_LEDGER\",\"entityId\":\"9223372036854775807\",\"platformUserId\":\"1000000042\",\"userId\":42,\"data\":{\"symbol\":\"BTCUSDT\",\"assetType\":\"USDT\",\"changeType\":\"handling_fee\",\"amount\":\"-0.000000000000000001\",\"walletBalanceBefore\":\"10.000000000000000001\",\"walletBalanceAfter\":\"10\",\"frozenBalanceBefore\":\"0.000000000000000001\",\"frozenBalanceAfter\":\"0\",\"referenceId\":\"8001\",\"createdAt\":1799913600123,\"userId\":42,\"description\":\"internal-event-key\"}}],\"nextCursor\":\"\",\"hasMore\":false}}");
        PerpetualResponse response = client(transport).listPerpetualLedger(ledger());
        assertEquals(3, response.data.items.size());
        PerpetualRecord record = response.data.items.get(0);
        assertEquals("9223372036854775805", record.entityId);
        assertEquals("9223372036854775806", response.data.items.get(1).entityId);
        assertEquals(record.data.ledgerId, response.data.items.get(1).data.ledgerId);
        assertNull(response.data.items.get(2).data.ledgerId);
        assertEquals("-0.000000000000000001", record.data.amount);
        assertEquals("10.000000000000000001", record.data.walletBalanceBefore);
        assertEquals("10", record.data.walletBalanceAfter);
        assertEquals("0.000000000000000001", record.data.frozenBalanceBefore);
        assertEquals("0", record.data.frozenBalanceAfter);
        assertEquals(1799913600123L, record.data.createdAt.longValue());
        assertNull(record.data.walletDelta);
        assertNull(record.data.frozenDelta);
        assertNull(record.data.usedDelta);
        assertNull(record.data.feeDelta);
        assertNull(record.data.pnlDelta);
        assertNull(record.data.accountVersion);
        assertNull(record.data.occurredAt);
        String encoded = MAPPER.writeValueAsString(record);
        assertFalse(encoded.contains("\"userId\""));
        assertFalse(encoded.contains("description"));
    }

    @Test
    void failuresAreNotRetriedAndMalformedSuccessIsNotEmptyData() {
        Capture transport = new Capture("{\"code\":102002,\"message\":\"invalid public user IDs\"}");
        AgentApiException error = assertThrows(AgentApiException.class, () -> client(transport).listPerpetualTrades(trades()));
        assertEquals(102002, error.getCode());
        assertEquals(1, transport.calls);
        transport.response = "{}";
        assertThrows(AgentSdkException.class, () -> client(transport).listPerpetualTrades(trades()));
        transport.response = "{\"code\":0,\"message\":\"success\",\"data\":{\"items\":[],\"hasMore\":true,\"nextCursor\":\"\"}}";
        assertThrows(AgentSdkException.class, () -> client(transport).listPerpetualTrades(trades()));
        transport.failIO = true;
        assertThrows(AgentSdkException.class, () -> client(transport).listPerpetualTrades(trades()));
        assertEquals(4, transport.calls);
    }

    @Test
    void rateLimitErrorsPreserveCodeAndRetryOnlyWhenCallerResendsSamePage() throws Exception {
        for (int code : Arrays.asList(6905, 6904)) {
            String response = "{\"code\":" + code + ",\"message\":\"query rejected\"}";
            Capture transport = new Capture(response);
            AgentClient client = client(transport);
            CurrentPerpetualPositionsRequest request = new CurrentPerpetualPositionsRequest();
            request.platformUserIds = Arrays.asList("1000000042", "1000000043");
            request.symbol = "BTCUSDT";
            request.limit = 10;
            request.cursor = "same-page-cursor";
            JsonNode previous = null;
            // 第二次调用由商户主动发起；错误本身不能触发 SDK 内部重试或修改分页范围。
            for (int attempt = 1; attempt <= 2; attempt++) {
                AgentApiException error = assertThrows(AgentApiException.class,
                        () -> client.listCurrentPerpetualPositions(request));
                assertEquals(code, error.getCode());
                assertEquals(200, error.getHttpStatus());
                assertEquals(response, error.getResponseBody());
                assertEquals(attempt, transport.calls);
                assertEquals("same-page-cursor", request.cursor);
                JsonNode body = MAPPER.readTree(transport.body);
                assertEquals("same-page-cursor", body.path("cursor").asText());
                assertEquals("BTCUSDT", body.path("symbol").asText());
                assertEquals(10, body.path("limit").asInt());
                assertEquals(MAPPER.valueToTree(request.platformUserIds), body.path("platformUserIds"));
                checkRoute(transport, "positions/current");
                if (previous != null) {
                    assertNotEquals(previous.path("nonce"), body.path("nonce"));
                    assertNotEquals(previous.path("sign"), body.path("sign"));
                }
                previous = body;
            }
        }
    }

    private static void checkRoute(Capture transport, String resource) throws Exception {
        assertEquals("POST", transport.method);
        assertEquals("http://agent.test/v1/agent/perpetual/" + resource, transport.url);
        Map<String, Object> body = MAPPER.readValue(transport.body, Map.class);
        assertEquals(AgentSigner.signParams(body, "secret"), body.get("sign"));
        assertFalse(body.containsKey("currency"));
        assertFalse(body.containsKey("agentId"));
    }

    private static PerpetualLedgerRequest ledger() {
        PerpetualLedgerRequest request = new PerpetualLedgerRequest();
        request.platformUserIds = Arrays.asList("1000000042", "1000000043");
        return request;
    }

    private static PerpetualTradesRequest trades() {
        PerpetualTradesRequest request = new PerpetualTradesRequest();
        request.platformUserIds = Arrays.asList("1000000042", "1000000043");
        return request;
    }

    private static AgentClient client(Capture transport) {
        final int[] count = {0};
        return new AgentClient(AgentClientConfig.builder().baseUrl("http://agent.test").agentCode("AGENT001")
                .apiSecret("secret").defaultCurrency("USDT").httpTransport(transport)
                .clock(Clock.fixed(Instant.ofEpochSecond(1713024000), ZoneOffset.UTC))
                .nonceGenerator(() -> "nonce-" + (++count[0])).build());
    }

    private static class Capture implements AgentHttpTransport {
        int calls;
        String response, method, url, body;
        boolean failIO;
        Capture(String response) { this.response = response; }
        public AgentHttpResponse send(String method, String url, Map<String, String> headers, byte[] body,
                                      java.time.Duration timeout) throws IOException {
            calls++;
            this.method = method;
            this.url = url;
            this.body = new String(body, StandardCharsets.UTF_8);
            if (failIO) { throw new IOException("offline transport failure"); }
            return new AgentHttpResponse(200, response);
        }
    }
}
