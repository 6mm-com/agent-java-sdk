package com.sixmm.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {
    @Test
    void verifyAcceptsSignatureBuiltFromTimestampNonceAndRawBody() {
        byte[] body = "{\"orderType\":\"transfer\",\"orderId\":123,\"targetStatus\":\"SUCCESS\"}"
                .getBytes(StandardCharsets.UTF_8);
        String sign = AgentSigner.signWebhook("secret", "1713024000", "nonce-1", body);

        assertTrue(WebhookVerifier.verify("secret", "1713024000", "nonce-1", body, sign));
    }

    @Test
    void verifyRejectsInvalidSignature() {
        byte[] body = "{\"orderType\":\"transfer\"}".getBytes(StandardCharsets.UTF_8);

        assertFalse(WebhookVerifier.verify("secret", "1713024000", "nonce-1", body, "bad-signature"));
    }

    @Test
    void idempotencyKeyUsesOrderTypeOrderIdAndTargetStatus() {
        byte[] body = "{\"orderType\":\"transfer\",\"orderId\":123,\"targetStatus\":\"SUCCESS\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertEquals("transfer:123:SUCCESS", WebhookVerifier.idempotencyKey(body));
    }
}
