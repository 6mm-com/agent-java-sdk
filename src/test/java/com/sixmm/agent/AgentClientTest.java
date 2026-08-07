package com.sixmm.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixmm.agent.model.BindRequest;
import com.sixmm.agent.model.BindResponse;
import com.sixmm.agent.model.Direction;
import com.sixmm.agent.model.CreateEmbedTokenRequest;
import com.sixmm.agent.model.CreateEmbedTokenResponse;
import com.sixmm.agent.model.CreateEntryUrlRequest;
import com.sixmm.agent.model.CreateEntryUrlResponse;
import com.sixmm.agent.model.ListSupportedFiatCurrenciesResponse;
import com.sixmm.agent.model.QueryExchangeRatesRequest;
import com.sixmm.agent.model.QueryExchangeRatesResponse;
import com.sixmm.agent.model.QueryOrderRequest;
import com.sixmm.agent.model.QueryUserAssetsRequest;
import com.sixmm.agent.model.QueryUserAssetsResponse;
import com.sixmm.agent.model.TransferAllOutRequest;
import com.sixmm.agent.model.TransferAllOutResponse;
import com.sixmm.agent.model.TransferRequest;
import com.sixmm.agent.model.TransferResponse;
import com.sixmm.agent.model.VersionResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void transferInjectsAgentCodeTimestampNonceAndSign() throws Exception {
        CapturingTransport transport = new CapturingTransport(200, "{\"code\":0,\"message\":\"success\",\"orderStatus\":\"PROCESSING\"}");
        AgentClient client = newClient(transport);

        TransferResponse response = client.transfer(TransferRequest.fixed("A-1", "u-1", Direction.IN, "USDT", "10.00"));

        assertEquals("PROCESSING", response.orderStatus);
        assertEquals("POST", transport.lastMethod);
        assertEquals("http://agent.test/v1/agent/transfer", transport.lastUrl);
        assertEquals("application/json", transport.lastHeaders.get("Content-Type"));

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("AGENT001", body.get("agentCode").asText());
        assertEquals("A-1", body.get("agentOrderNo").asText());
        assertEquals("u-1", body.get("agentUserId").asText());
        assertEquals("IN", body.get("direction").asText());
        assertEquals("USDT", body.get("currency").asText());
        assertEquals("10.00", body.get("amount").asText());
        assertEquals(1713024000L, body.get("timestamp").asLong());
        assertEquals("nonce-1", body.get("nonce").asText());

        Map<String, Object> signParams = new LinkedHashMap<String, Object>();
        signParams.put("agentOrderNo", "A-1");
        signParams.put("agentUserId", "u-1");
        signParams.put("direction", "IN");
        signParams.put("currency", "USDT");
        signParams.put("amount", "10.00");
        signParams.put("agentCode", "AGENT001");
        signParams.put("timestamp", 1713024000L);
        signParams.put("nonce", "nonce-1");
        assertEquals(AgentSigner.signParams(signParams, "secret"), body.get("sign").asText());
    }

    @Test
    void queryOrderThrowsAgentApiExceptionWhenBusinessCodeIsNonZero() {
        CapturingTransport transport = new CapturingTransport(200, "{\"code\":6101,\"message\":\"order not found\"}");
        AgentClient client = newClient(transport);

        AgentApiException err = assertThrows(AgentApiException.class,
                () -> client.queryOrder(QueryOrderRequest.of("missing", "TRANSFER_IN")));

        assertEquals(200, err.getHttpStatus());
        assertEquals(6101, err.getCode());
        assertTrue(err.getMessage().contains("order not found"));
    }

    @Test
    void bindHydratesSimulatedUserFlag() {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"platformUserId\":\"1188041528\",\"bindStatus\":\"BOUND\",\"isSimulatedUser\":true}");
        AgentClient client = newClient(transport);

        BindResponse response = client.bind(BindRequest.of("u-1").withUsername("Alice"));

        assertTrue(response.isSimulatedUser);
        assertTrue(transport.lastBody.contains("\"username\":\"Alice\""));
    }

    @Test
    void queryUserAssetsHydratesSimulatedUserFlag() {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"platformUserId\":\"1188041528\",\"walletBalance\":\"10\",\"availableBalance\":\"9\",\"isSimulatedUser\":true}");
        AgentClient client = newClient(transport);

        QueryUserAssetsResponse response = client.queryUserAssets(QueryUserAssetsRequest.of("1188041528"));

        assertTrue(response.isSimulatedUser);
    }

    @Test
    void versionUsesGetWithoutAgentSignatureBody() {
        CapturingTransport transport = new CapturingTransport(200, "{\"service\":\"agent\",\"version\":\"dev\",\"goVersion\":\"go1.24\",\"commit\":\"local\",\"date\":\"2026-05-26\"}");
        AgentClient client = newClient(transport);

        VersionResponse response = client.version();

        assertEquals("agent", response.service);
        assertEquals("GET", transport.lastMethod);
        assertEquals("http://agent.test/version", transport.lastUrl);
        assertEquals("", transport.lastBody);
    }

    @Test
    void transferUsesDefaultCurrencyWhenRequestCurrencyIsBlank() throws Exception {
        CapturingTransport transport = new CapturingTransport(200, "{\"code\":0,\"message\":\"success\",\"orderStatus\":\"PROCESSING\"}");
        AgentClient client = newClient(transport);

        client.transfer(TransferRequest.fixed("A-2", "u-2", Direction.OUT, "", "3.50"));

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("USDT", body.get("currency").asText());
    }

    @Test
    void transferCanUsePlatformUserIdAndHydratesBothUserIdentifiers() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"orderNo\":\"A-3\",\"orderStatus\":\"SUCCESS\",\"agentUserId\":\"u-3\",\"platformUserId\":\"1188041528\"}");
        AgentClient client = newClient(transport);

        TransferResponse response = client.transfer(
                TransferRequest.fixedByPlatformUserId("A-3", "1188041528", Direction.OUT, "USDT", "2.50"));

        assertEquals("u-3", response.agentUserId);
        assertEquals("1188041528", response.platformUserId);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("1188041528", body.get("platformUserId").asText());
        assertTrue(!body.has("agentUserId"));
    }

    @Test
    void createEntryUrlCanCarryReturnUrl() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"webUrl\":\"http://app.test/agent-entry?ticket=abc\",\"expireAt\":1777000060}");
        AgentClient client = newClient(transport);

        CreateEntryUrlResponse response = client.createEntryUrl(
                CreateEntryUrlRequest.of("u-1")
                        .withRedirectPath("/trade/BTCUSDT")
                        .withReturnUrl("https://partner.example/return#markets"));

        assertEquals("http://app.test/agent-entry?ticket=abc", response.webUrl);
        assertEquals(1777000060L, response.expireAt);
        assertEquals("POST", transport.lastMethod);
        assertEquals("http://agent.test/v1/agent/create-entry-url", transport.lastUrl);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("u-1", body.get("agentUserId").asText());
        assertEquals("/trade/BTCUSDT", body.get("redirectPath").asText());
        assertEquals("https://partner.example/return#markets", body.get("returnUrl").asText());

        Map<String, Object> signParams = new LinkedHashMap<String, Object>();
        signParams.put("agentUserId", "u-1");
        signParams.put("redirectPath", "/trade/BTCUSDT");
        signParams.put("returnUrl", "https://partner.example/return#markets");
        signParams.put("agentCode", "AGENT001");
        signParams.put("timestamp", 1713024000L);
        signParams.put("nonce", "nonce-1");
        assertEquals(AgentSigner.signParams(signParams, "secret"), body.get("sign").asText());
    }

    @Test
    void transferAllOutCanUsePlatformUserIdAndHydratesBothUserIdentifiers() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"orderNo\":\"A-4\",\"orderStatus\":\"SUCCESS\",\"amount\":\"8.50\",\"agentUserId\":\"u-4\",\"platformUserId\":\"1188041529\"}");
        AgentClient client = newClient(transport);

        TransferAllOutResponse response = client.transferAllOut(
                TransferAllOutRequest.byPlatformUserId("A-4", "1188041529", "USDT"));

        assertEquals("u-4", response.agentUserId);
        assertEquals("1188041529", response.platformUserId);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("1188041529", body.get("platformUserId").asText());
        assertTrue(!body.has("agentUserId"));
    }

    @Test
    void createEmbedTokenUsesSignedAgentEndpoint() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"embedToken\":\"embed-1\",\"expireAt\":1777000060}");
        AgentClient client = newClient(transport);

        CreateEmbedTokenResponse response = client.createEmbedToken(
                CreateEmbedTokenRequest.of("u-1", "portal-demo-1").withSymbol("ETHUSDT"));

        assertEquals("embed-1", response.embedToken);
        assertEquals(1777000060L, response.expireAt);
        assertEquals("POST", transport.lastMethod);
        assertEquals("http://agent.test/v1/agent/create-embed-token", transport.lastUrl);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("u-1", body.get("agentUserId").asText());
        assertEquals("portal-demo-1", body.get("channelId").asText());
        assertEquals("ETHUSDT", body.get("symbol").asText());
        assertEquals("AGENT001", body.get("agentCode").asText());
        assertEquals(1713024000L, body.get("timestamp").asLong());
        assertEquals("nonce-1", body.get("nonce").asText());

        Map<String, Object> signParams = new LinkedHashMap<String, Object>();
        signParams.put("agentUserId", "u-1");
        signParams.put("channelId", "portal-demo-1");
        signParams.put("symbol", "ETHUSDT");
        signParams.put("agentCode", "AGENT001");
        signParams.put("timestamp", 1713024000L);
        signParams.put("nonce", "nonce-1");
        assertEquals(AgentSigner.signParams(signParams, "secret"), body.get("sign").asText());
    }

    @Test
    void listSupportedFiatCurrenciesUsesSignedAgentEndpoint() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"targetCurrency\":\"USDT\",\"sourceCurrencies\":[\"AUD\",\"CNY\",\"USD\"]}");
        AgentClient client = newClient(transport);

        ListSupportedFiatCurrenciesResponse response = client.listSupportedFiatCurrencies();

        assertEquals("USDT", response.targetCurrency);
        assertEquals(3, response.sourceCurrencies.size());
        assertEquals("AUD", response.sourceCurrencies.get(0));
        assertEquals("POST", transport.lastMethod);
        assertEquals("http://agent.test/v1/agent/list-supported-fiat-currencies", transport.lastUrl);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("AGENT001", body.get("agentCode").asText());
        assertEquals(1713024000L, body.get("timestamp").asLong());
        assertEquals("nonce-1", body.get("nonce").asText());
        assertTrue(!body.has("sourceCurrencies"));

        Map<String, Object> signParams = new LinkedHashMap<String, Object>();
        signParams.put("agentCode", "AGENT001");
        signParams.put("timestamp", 1713024000L);
        signParams.put("nonce", "nonce-1");
        assertEquals(AgentSigner.signParams(signParams, "secret"), body.get("sign").asText());
    }

    @Test
    void queryExchangeRatesSerializesCurrenciesAndHydratesReferenceMetadata() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"snapshotVersion\":\"ecb-20260806-fixed-test\","
                        + "\"provider\":\"ECB\",\"sourceDate\":\"2026-08-06\",\"fetchedAt\":1786092926728,"
                        + "\"expiresAt\":1786579200000,\"pricingPolicy\":\"FIXED_PEG\","
                        + "\"usdtUsdRate\":\"1.000000000000000000\",\"rateType\":\"INDICATIVE_DAILY\","
                        + "\"usage\":\"REFERENCE_ONLY\",\"rateMeaning\":\"1 sourceCurrency = rate USDT\","
                        + "\"rates\":[{\"sourceCurrency\":\"CNY\",\"targetCurrency\":\"USDT\","
                        + "\"rate\":\"0.148168117281573339\"}]}");
        AgentClient client = newClient(transport);

        QueryExchangeRatesResponse response = client.queryExchangeRates(
                QueryExchangeRatesRequest.of(" cny ", "EUR", "", null, "usd"));

        assertEquals("ecb-20260806-fixed-test", response.snapshotVersion);
        assertEquals("ECB", response.provider);
        assertEquals("2026-08-06", response.sourceDate);
        assertEquals(1786092926728L, response.fetchedAt);
        assertEquals(1786579200000L, response.expiresAt);
        assertEquals("FIXED_PEG", response.pricingPolicy);
        assertEquals("1.000000000000000000", response.usdtUsdRate);
        assertEquals("INDICATIVE_DAILY", response.rateType);
        assertEquals("REFERENCE_ONLY", response.usage);
        assertEquals("1 sourceCurrency = rate USDT", response.rateMeaning);
        assertEquals(1, response.rates.size());
        assertEquals("CNY", response.rates.get(0).sourceCurrency);
        assertEquals("USDT", response.rates.get(0).targetCurrency);
        assertEquals("0.148168117281573339", response.rates.get(0).rate);
        assertEquals("http://agent.test/v1/agent/query-exchange-rates", transport.lastUrl);

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertEquals("CNY,EUR,USD", body.get("sourceCurrencies").asText());

        Map<String, Object> signParams = new LinkedHashMap<String, Object>();
        signParams.put("sourceCurrencies", "CNY,EUR,USD");
        signParams.put("agentCode", "AGENT001");
        signParams.put("timestamp", 1713024000L);
        signParams.put("nonce", "nonce-1");
        assertEquals(AgentSigner.signParams(signParams, "secret"), body.get("sign").asText());
    }

    @Test
    void queryExchangeRatesWithoutRequestOmitsCurrencyFilter() throws Exception {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"code\":0,\"message\":\"success\",\"rates\":[]}");
        AgentClient client = newClient(transport);

        client.queryExchangeRates();

        JsonNode body = MAPPER.readTree(transport.lastBody);
        assertTrue(!body.has("sourceCurrencies"));
        assertEquals("http://agent.test/v1/agent/query-exchange-rates", transport.lastUrl);
    }

    private AgentClient newClient(CapturingTransport transport) {
        return new AgentClient(AgentClientConfig.builder()
                .baseUrl("http://agent.test")
                .agentCode("AGENT001")
                .apiSecret("secret")
                .defaultCurrency("USDT")
                .clock(Clock.fixed(Instant.ofEpochSecond(1713024000L), ZoneOffset.UTC))
                .nonceGenerator(() -> "nonce-1")
                .httpTransport(transport)
                .build());
    }

    private static final class CapturingTransport implements AgentHttpTransport {
        private final int status;
        private final String responseBody;
        private String lastMethod;
        private String lastUrl;
        private Map<String, String> lastHeaders = new LinkedHashMap<String, String>();
        private String lastBody = "";

        private CapturingTransport(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public AgentHttpResponse send(String method, String url, Map<String, String> headers, byte[] body, java.time.Duration timeout) throws IOException {
            this.lastMethod = method;
            this.lastUrl = url;
            this.lastHeaders = new LinkedHashMap<String, String>(headers);
            this.lastBody = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            return new AgentHttpResponse(status, responseBody);
        }
    }
}
