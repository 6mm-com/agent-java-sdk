package com.sixmm.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class UrlConnectionAgentHttpTransport implements AgentHttpTransport {
    @Override
    public AgentHttpResponse send(String method, String url, Map<String, String> headers, byte[] body, Duration timeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMillis(timeout));
        conn.setReadTimeout(timeoutMillis(timeout));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            conn.setRequestProperty(header.getKey(), header.getValue());
        }

        if (body != null && body.length > 0) {
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
        }

        int status = conn.getResponseCode();
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = stream == null ? "" : readString(stream);
        conn.disconnect();
        return new AgentHttpResponse(status, responseBody);
    }

    private int timeoutMillis(Duration timeout) {
        Duration value = timeout == null ? Duration.ofSeconds(10) : timeout;
        long millis = value.toMillis();
        if (millis <= 0) {
            return 0;
        }
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) millis;
    }

    private String readString(InputStream stream) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }
}
