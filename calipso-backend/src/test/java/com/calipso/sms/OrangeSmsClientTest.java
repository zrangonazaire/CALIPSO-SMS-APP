package com.calipso.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrangeSmsClientTest {
    @Test
    void usesOfficialPayloadCachesTokensAndRefreshesAfterUnauthorized() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var tokens = new AtomicInteger();
        var sends = new AtomicInteger();
        var bodies = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var paths = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var auth = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/token", e -> {
            auth.add(e.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int n = tokens.incrementAndGet();
            byte[] data = ("{\"access_token\":\"token" + n + "\",\"expires_in\":3600}").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, data.length); e.getResponseBody().write(data); e.close();
        });
        server.createContext("/outbound/", e -> {
            paths.add(e.getRequestURI().getRawPath());
            bodies.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(e.getRequestHeaders().getFirst("Authorization"));
            int status = sends.incrementAndGet() == 2 ? 401 : 201;
            byte[] data = "{\"outboundSMSMessageRequest\":{\"resourceURL\":\"https://api.orange.com/requests/id1\"}}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(status, data.length); e.getResponseBody().write(data); e.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var props = new OrangeSmsProperties(base + "/token", base + "/outbound/{senderAddress}/requests",
                    null, null, null, null, null, null, "225", null, null, null, 5, 5);
            var client = new OrangeSmsClient(props);
            assertTrue(client.sendSmsForCompany("Bonjour", "MAGISER", "0701020304", "client", "secret", "MAGISER").sent());
            assertTrue(client.sendSmsForCompany("Bonjour", null, "+2250701020304", "client", "secret", "MAGISER").sent());
            assertEquals(2, tokens.get()); // initial token, then refresh only after HTTP 401
            assertEquals(3, sends.get());
            assertEquals("/outbound/tel%3A%2B2250000/requests", paths.getFirst());
            assertEquals("grant_type=client_credentials", bodies.getFirst());
            assertEquals("Basic " + Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)), auth.getFirst());
            var payload = new ObjectMapper().readTree(bodies.get(1)).path("outboundSMSMessageRequest");
            assertEquals("tel:+2250000", payload.path("senderAddress").asText());
            assertEquals("tel:+2250701020304", payload.path("address").asText());
            assertEquals("MAGISER", payload.path("senderName").asText());
            assertEquals("Bonjour", payload.path("outboundSMSTextMessage").path("message").asText());
            assertFalse(client.sendSmsForCompany("Bonjour", null, "+33601020304", "client", "secret", null).sent());
            assertFalse(client.sendSmsForCompany("Bonjour", null, "0701020304", "client", "secret", "INVALID-NAME").sent());
            assertEquals(3, sends.get());
        } finally { server.stop(0); }
    }

    @Test
    void neverClaimsDeliveryWhenOrangeRejectsTheSms() {
        var orange = mock(OrangeSmsClient.class);
        when(orange.normalizePhone("0701020304")).thenReturn("+2250701020304");
        when(orange.sendSms("Bonjour", null, "+2250701020304")).thenReturn(SmsDeliveryResult.failed(403, "Forfait expiré"));
        var gateway = new MultichannelGatewayService(orange, mock(com.calipso.integration.CompanyIntegrationSettingsService.class), mock(SmtpClient.class));
        var result = gateway.dispatch(com.calipso.campaign.CampaignChannel.SMS, "Bonjour", null, null, "0701020304", null, null, null);
        assertFalse(result.sent());
        assertEquals(403, result.statusCode());
        assertNull(result.providerResourceId());
    }
}
