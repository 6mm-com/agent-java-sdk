package com.freedex.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AgentClientConfigTest {
    @Test
    void configRequiresBaseUrlAgentCodeAndSecret() {
        assertThrows(AgentSdkException.class, () -> AgentClientConfig.builder().build());
        assertThrows(AgentSdkException.class, () -> AgentClientConfig.builder()
                .baseUrl("http://agent.test")
                .apiSecret("secret")
                .build());
        assertThrows(AgentSdkException.class, () -> AgentClientConfig.builder()
                .baseUrl("http://agent.test")
                .agentCode("AGENT001")
                .build());
    }

    @Test
    void configNormalizesBaseUrlAndKeepsInjectableClockAndNonce() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1713024000L), ZoneOffset.UTC);
        AgentClientConfig config = AgentClientConfig.builder()
                .baseUrl("http://agent.test/")
                .agentCode("AGENT001")
                .apiSecret("secret")
                .defaultCurrency("USDT")
                .timeout(Duration.ofSeconds(3))
                .clock(clock)
                .nonceGenerator(() -> "nonce-1")
                .build();

        assertEquals("http://agent.test", config.getBaseUrl());
        assertEquals("AGENT001", config.getAgentCode());
        assertEquals("USDT", config.getDefaultCurrency());
        assertEquals(1713024000L, config.getClock().instant().getEpochSecond());
        assertEquals("nonce-1", config.getNonceGenerator().newNonce());
    }
}
