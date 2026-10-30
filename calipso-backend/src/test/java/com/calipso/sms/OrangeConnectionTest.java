package com.calipso.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OrangeConnectionTest {
    @Test
    void reportsAuthenticationFailureWhenAnExpiredTokenCannotBeRenewed() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var tokens = new AtomicInteger();
        server.createContext("/token", exchange -> {
            byte[] body = "{\"access_token\":\"old-token\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            if (tokens.incrementAndGet() == 1) { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            else exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.createContext("/contracts", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var properties = new OrangeSmsProperties(base + "/token", base + "/sms", null,
                    base + "/contracts", null, null, null, null, "225", null, null, null, 5, 5);
            assertThrows(java.io.IOException.class, () -> new OrangeSmsClient(properties).checkCompanyConnection("id", "secret"));
        } finally { server.stop(0); }
    }

    @Test
    void obtainsABearerAndReadsOnlyTheCompanyContractsWithoutSendingOrReturningSecrets() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var tokenRequests = new AtomicInteger(); var contractRequests = new AtomicInteger(); var smsRequests = new AtomicInteger();
        server.createContext("/token", exchange -> {
            tokenRequests.incrementAndGet();
            byte[] data = "{\"access_token\":\"private-token\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
        server.createContext("/contracts", exchange -> {
            contractRequests.incrementAndGet();
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("Bearer private-token", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data = "[{\"id\":\"active\",\"status\":\"ACTIVE\",\"availableUnits\":12,\"expirationDate\":\"2099-11-23T23:59:59Z\"},{\"id\":\"expired\",\"status\":\"ACTIVE\",\"availableUnits\":100,\"expirationDate\":\"2020-01-01T00:00:00Z\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
        server.createContext("/sms", exchange -> { smsRequests.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var properties = new OrangeSmsProperties(base + "/token", base + "/sms/{senderAddress}", null,
                    base + "/contracts", null, null, null, null, "225", null, null, null, 5, 5);
            var client = new OrangeSmsClient(properties);
            var result = client.checkCompanyConnection("company-client", "private-secret");
            assertTrue(result.authenticated()); assertTrue(result.contractsAccessible()); assertEquals(12, result.availableUnits());
            assertEquals("Bearer", result.tokenType()); assertNotNull(result.tokenExpiresAt());
            client.checkCompanyConnection("company-client", "private-secret");
            assertEquals(1, tokenRequests.get()); assertEquals(2, contractRequests.get()); assertEquals(0, smsRequests.get());
            String response = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
            assertFalse(response.contains("private-token")); assertFalse(response.contains("private-secret"));
        } finally { server.stop(0); }
    }
}
