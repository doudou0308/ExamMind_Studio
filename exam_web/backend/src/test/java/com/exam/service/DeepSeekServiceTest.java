package com.exam.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekServiceTest {
    @Test
    void missingKeyFailsBeforeSendingRequest() {
        DeepSeekService service = new DeepSeekService("", "http://127.0.0.1:1");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.call("test", 10, 0));
        assertTrue(error.getMessage().contains("DEEPSEEK_API_KEY"));
    }

    @Test
    void usesConfiguredEndpointAndCredential() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            DeepSeekService service = new DeepSeekService("test-api-key",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            assertEquals("{\"ok\":true}", service.call("test", 10, 0));
            assertEquals("Bearer test-api-key", authorization.get());
            assertEquals("POST", method.get());
        } finally {
            server.stop(0);
        }
    }
}
