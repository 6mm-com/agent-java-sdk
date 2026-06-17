package com.sixmm.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public interface AgentHttpTransport {
    AgentHttpResponse send(String method, String url, Map<String, String> headers, byte[] body, Duration timeout) throws IOException;
}
