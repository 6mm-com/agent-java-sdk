package com.freedex.agent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AgentSigner {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String HMAC_SHA256 = "HmacSHA256";

    private AgentSigner() {
    }

    public static String buildSignPayload(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        List<String> keys = new ArrayList<>(params.keySet());
        keys.removeIf(key -> "sign".equals(key) || stringify(params.get(key)).isEmpty());
        keys.sort(Comparator.naturalOrder());

        List<String> parts = new ArrayList<>(keys.size());
        for (String key : keys) {
            parts.add(key + "=" + stringify(params.get(key)));
        }
        return String.join("&", parts);
    }

    public static String signParams(Map<String, ?> params, String apiSecret) {
        return hmacSha256Hex(apiSecret, buildSignPayload(params));
    }

    public static String signWebhook(String apiSecret, String timestamp, String nonce, byte[] body) {
        String bodyText = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return hmacSha256Hex(apiSecret, timestamp + nonce + bodyText);
    }

    static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AgentSdkException("failed to calculate hmac signature", e);
        }
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return String.valueOf(value);
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }
}
