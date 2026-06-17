package com.sixmm.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.Locale;

public final class WebhookVerifier {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookVerifier() {
    }

    public static boolean verify(String apiSecret, String timestamp, String nonce, byte[] body, String signature) {
        if (isBlank(apiSecret) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return false;
        }
        byte[] expected = decodeHex(AgentSigner.signWebhook(apiSecret, timestamp, nonce, body));
        byte[] actual = decodeHex(signature);
        if (expected.length == 0 || actual.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    public static String idempotencyKey(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            String orderType = text(root.get("orderType"));
            String orderId = text(root.get("orderId"));
            String targetStatus = text(root.get("targetStatus"));
            if (isBlank(orderType) || isBlank(orderId) || isBlank(targetStatus)) {
                return "";
            }
            return orderType + ":" + orderId + ":" + targetStatus;
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static byte[] decodeHex(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0 || normalized.length() % 2 != 0) {
            return new byte[0];
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return new byte[0];
            }
            out[i / 2] = (byte) ((high << 4) + low);
        }
        return out;
    }
}
