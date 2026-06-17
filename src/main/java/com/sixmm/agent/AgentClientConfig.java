package com.sixmm.agent;

import java.time.Clock;
import java.time.Duration;

public final class AgentClientConfig {
    private final String baseUrl;
    private final String agentCode;
    private final String apiSecret;
    private final String defaultCurrency;
    private final Duration timeout;
    private final Clock clock;
    private final NonceGenerator nonceGenerator;
    private final AgentHttpTransport httpTransport;

    private AgentClientConfig(Builder builder) {
        this.baseUrl = normalizeBaseUrl(requireNotBlank(builder.baseUrl, "baseUrl is required"));
        this.agentCode = requireNotBlank(builder.agentCode, "agentCode is required");
        this.apiSecret = requireNotBlank(builder.apiSecret, "apiSecret is required");
        this.defaultCurrency = trimToEmpty(builder.defaultCurrency);
        this.timeout = builder.timeout == null ? Duration.ofSeconds(10) : builder.timeout;
        this.clock = builder.clock == null ? Clock.systemUTC() : builder.clock;
        this.nonceGenerator = builder.nonceGenerator == null ? NonceGenerator.secureRandom() : builder.nonceGenerator;
        this.httpTransport = builder.httpTransport == null ? new UrlConnectionAgentHttpTransport() : builder.httpTransport;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Clock getClock() {
        return clock;
    }

    public NonceGenerator getNonceGenerator() {
        return nonceGenerator;
    }

    public AgentHttpTransport getHttpTransport() {
        return httpTransport;
    }

    private static String requireNotBlank(String value, String message) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            throw new AgentSdkException(message);
        }
        return trimmed;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String baseUrl;
        private String agentCode;
        private String apiSecret;
        private String defaultCurrency;
        private Duration timeout;
        private Clock clock;
        private NonceGenerator nonceGenerator;
        private AgentHttpTransport httpTransport;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder agentCode(String agentCode) {
            this.agentCode = agentCode;
            return this;
        }

        public Builder apiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
            return this;
        }

        public Builder defaultCurrency(String defaultCurrency) {
            this.defaultCurrency = defaultCurrency;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder nonceGenerator(NonceGenerator nonceGenerator) {
            this.nonceGenerator = nonceGenerator;
            return this;
        }

        public Builder httpTransport(AgentHttpTransport httpTransport) {
            this.httpTransport = httpTransport;
            return this;
        }

        public AgentClientConfig build() {
            return new AgentClientConfig(this);
        }
    }
}
