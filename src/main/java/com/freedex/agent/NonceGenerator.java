package com.freedex.agent;

import java.security.SecureRandom;

@FunctionalInterface
public interface NonceGenerator {
    String newNonce();

    static NonceGenerator secureRandom() {
        SecureRandom random = new SecureRandom();
        return () -> {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            char[] out = new char[bytes.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                out[i * 2] = hex[value >>> 4];
                out[i * 2 + 1] = hex[value & 0x0f];
            }
            return new String(out);
        };
    }
}
