package com.sixmm.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSignerTest {
    @Test
    void buildPayloadSortsKeysAndSkipsEmptyAndSign() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sign", "ignored");
        params.put("nonce", "nonce-1");
        params.put("amount", "10.00");
        params.put("currency", "");
        params.put("timestamp", 1713024000L);
        params.put("agentCode", "AGENT001");

        assertEquals(
                "agentCode=AGENT001&amount=10.00&nonce=nonce-1&timestamp=1713024000",
                AgentSigner.buildSignPayload(params));
    }

    @Test
    void signParamsUsesHmacSha256HexOverSortedPayload() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("agentCode", "AGENT001");
        params.put("amount", "10.00");
        params.put("nonce", "nonce-1");
        params.put("timestamp", 1713024000L);

        assertEquals(
                "dcb87a1b3133116a34df461f3bc61824b657ea42be4342ddf2335461df40d383",
                AgentSigner.signParams(params, "secret"));
    }

    @Test
    void signWebhookUsesTimestampNonceAndRawBody() {
        byte[] body = "{\"orderType\":\"transfer\",\"orderId\":123}".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                AgentSigner.hmacSha256Hex("secret", "1713024000nonce-1" + new String(body, StandardCharsets.UTF_8)),
                AgentSigner.signWebhook("secret", "1713024000", "nonce-1", body));
    }
}
