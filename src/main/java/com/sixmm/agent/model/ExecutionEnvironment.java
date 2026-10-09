package com.sixmm.agent.model;

public final class ExecutionEnvironment {
    public static final String PRODUCTION = "PRODUCTION";
    public static final String SIMULATED = "SIMULATED";
    private ExecutionEnvironment() {}
    public static String require(String value) {
        if (!PRODUCTION.equals(value) && !SIMULATED.equals(value)) {
            throw new IllegalArgumentException("Invalid environment");
        }
        return value;
    }
}
