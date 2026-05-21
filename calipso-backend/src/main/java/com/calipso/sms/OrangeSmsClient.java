package com.calipso.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrangeSmsClient {

    private final OrangeSmsProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public SmsDeliveryResult sendSms(String message, String senderPhone, String recipientPhone, String senderName) {
        try {
            String senderAddress = normalizePhone(firstNotBlank(senderPhone, properties.defaultSenderAddress()));
            String recipientAddress = normalizePhone(recipientPhone);

            if (senderAddress.isBlank()) {
                return SmsDeliveryResult.failed(0, "Numero expediteur Orange non configure");
            }

            String token = getAccessToken();
            String encodedSenderAddress = URLEncoder.encode("tel:" + senderAddress, StandardCharsets.UTF_8);
            String url = properties.outboundUrl().replace("{senderAddress}", encodedSenderAddress);

            Map<String, Object> payload = Map.of(
                    "outboundSMSMessageRequest", Map.of(
                            "address", "tel:" + recipientAddress,
                            "senderAddress", "tel:" + senderAddress,
                            "senderName", senderName == null ? "" : senderName,
                            "outboundSMSTextMessage", Map.of("message", message)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return SmsDeliveryResult.sent(response.statusCode());
            }

            return SmsDeliveryResult.failed(response.statusCode(), response.body());
        } catch (Exception exception) {
            return SmsDeliveryResult.failed(0, exception.getMessage());
        }
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return accessToken;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .header("Authorization", "Basic " + basicAuthorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token Orange refuse: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode body = objectMapper.readTree(response.body());
        accessToken = body.path("access_token").asText();
        long expiresIn = body.path("expires_in").asLong(3600);
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return accessToken;
    }

    private String basicAuthorization() {
        if (properties.basicAuthorization() != null && !properties.basicAuthorization().isBlank()) {
            return properties.basicAuthorization().replaceFirst("(?i)^Basic\\s+", "");
        }

        if (properties.clientId() == null || properties.clientSecret() == null
                || properties.clientId().isBlank() || properties.clientSecret().isBlank()) {
            throw new IllegalStateException("Identifiants Orange SMS non configures");
        }

        String credentials = properties.clientId() + ":" + properties.clientSecret();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }

        String cleaned = phone.replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .trim();

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith(properties.defaultCountryCode())) {
            return "+" + cleaned;
        }

        if (cleaned.startsWith("01") || cleaned.startsWith("05") || cleaned.startsWith("07")) {
            return "+" + properties.defaultCountryCode() + cleaned;
        }

        return cleaned;
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
    }

    private String firstNotBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
