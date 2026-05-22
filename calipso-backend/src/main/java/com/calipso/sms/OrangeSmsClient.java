package com.calipso.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrangeSmsClient {

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CLIENT_CREDENTIALS_BODY = "grant_type=client_credentials";
    private static final long TOKEN_EXPIRY_SAFETY_SECONDS = 30;
    private static final int SMS_SEND_MAX_ATTEMPTS = 3;
    private static final long SMS_RETRY_DELAY_MS = 1_000;

    private final OrangeSmsProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public SmsDeliveryResult sendSms(String message, String senderPhone, String recipientPhone) {
        try {
            log.info(
                    "[Orange SMS] Point d'arret sendSms: message={}, senderPhone={}, recipientPhone={}",
                    message,
                    senderPhone,
                    recipientPhone
            );

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
                            "outboundSMSTextMessage", Map.of("message", message)
                    )
            );
            String requestBody = objectMapper.writeValueAsString(payload);

            log.info("[Orange SMS] Payload envoye a Orange: {}", requestBody);
            log.info("[Orange SMS] Requete envoyee: url={}", url);

            HttpResponse<String> response = sendSmsRequestWithRetry(url, requestBody, token);
            if (response.statusCode() == 201) {
                String providerResourceUrl = extractProviderResourceUrl(response);
                String providerResourceId = extractProviderResourceId(providerResourceUrl);
                log.info(
                        "[Orange SMS] SMS accepte par Orange: statusCode={}, providerResourceId={}, providerResourceUrl={}",
                        response.statusCode(),
                        providerResourceId,
                        providerResourceUrl
                );

                return SmsDeliveryResult.sent(
                        response.statusCode(),
                        response.body(),
                        providerResourceUrl,
                        providerResourceId
                );
            }

            return SmsDeliveryResult.failed(response.statusCode(), response.body());
        } catch (Exception exception) {
            return SmsDeliveryResult.failed(0, exception.getMessage());
        }
    }

    private String extractProviderResourceUrl(HttpResponse<String> response) {
        try {
            JsonNode body = objectMapper.readTree(response.body());
            String resourceUrl = body.path("outboundSMSMessageRequest").path("resourceURL").asText(null);
            if (resourceUrl != null && !resourceUrl.isBlank()) {
                return resourceUrl;
            }
        } catch (Exception exception) {
            log.warn("[Orange SMS] Impossible de lire resourceURL dans la reponse Orange: {}", exception.getMessage());
        }

        return response.headers().firstValue("Location").orElse(null);
    }

    private String extractProviderResourceId(String providerResourceUrl) {
        if (providerResourceUrl == null || providerResourceUrl.isBlank()) {
            return null;
        }

        String cleaned = providerResourceUrl.trim();
        int queryIndex = cleaned.indexOf('?');
        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }

        int lastSlashIndex = cleaned.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == cleaned.length() - 1) {
            return cleaned;
        }

        return cleaned.substring(lastSlashIndex + 1);
    }

    private HttpResponse<String> sendSmsRequestWithRetry(String url, String requestBody, String token)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;

        for (int attempt = 1; attempt <= SMS_SEND_MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                    .setHeader("Authorization", "Bearer " + token)
                    .setHeader("Content-Type", APPLICATION_JSON)
                    .setHeader("Accept", APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            log.info(
                    "[Orange SMS] Reponse recue: tentative={}, statusCode={}, location={}, body={}",
                    attempt,
                    response.statusCode(),
                    response.headers().firstValue("Location").orElse(""),
                    response.body()
            );

            if (!isRetryableStatus(response.statusCode()) || attempt == SMS_SEND_MAX_ATTEMPTS) {
                return response;
            }

            Thread.sleep(SMS_RETRY_DELAY_MS * attempt);
        }

        return response;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public int fetchAvailableSmsUnits() {
        return fetchSmsContracts().stream()
                .filter(contract -> "ACTIVE".equalsIgnoreCase(contract.status()))
                .mapToInt(contract -> contract.availableUnits() == null ? 0 : contract.availableUnits())
                .sum();
    }

    public List<OrangeSmsContract> fetchSmsContracts() {
        try {
            String token = getAccessToken();
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.contractsUrl()))
                    .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                    .setHeader("Authorization", "Bearer " + token)
                    .setHeader("Accept", APPLICATION_JSON)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[Orange SMS] Reponse contrats: statusCode={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Solde Orange refuse: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode contracts = objectMapper.readTree(response.body());
            if (!contracts.isArray()) {
                throw new IllegalStateException("Solde Orange refuse: reponse contrats invalide");
            }

            List<OrangeSmsContract> results = new ArrayList<>();
            for (JsonNode contract : contracts) {
                results.add(new OrangeSmsContract(
                        contract.path("id").asText(null),
                        contract.path("type").asText(null),
                        contract.path("country").asText(null),
                        contract.path("offerName").asText(null),
                        contract.path("availableUnits").isMissingNode() ? null : contract.path("availableUnits").asInt(),
                        contract.path("requestedUnits").isMissingNode() ? null : contract.path("requestedUnits").asInt(),
                        contract.path("status").asText(null),
                        contract.path("expirationDate").asText(null),
                        contract.path("creationDate").asText(null),
                        contract.path("lastUpdateDate").asText(null)
                ));
            }

            int availableUnits = results.stream()
                    .filter(contract -> "ACTIVE".equalsIgnoreCase(contract.status()))
                    .mapToInt(contract -> contract.availableUnits() == null ? 0 : contract.availableUnits())
                    .sum();
            log.info("[Orange SMS] Contrats Orange actifs: {}, solde disponible: {} unite(s)",
                    results.stream().filter(contract -> "ACTIVE".equalsIgnoreCase(contract.status())).count(),
                    availableUnits
            );
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de recuperer le solde Orange SMS: " + exception.getMessage(), exception);
        }
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(TOKEN_EXPIRY_SAFETY_SECONDS))) {
            return accessToken;
        }

        HttpResponse<String> response = requestOAuthAccessToken();
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token Orange refuse: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode body = objectMapper.readTree(response.body());
        accessToken = body.path("access_token").asText();
        String tokenType = body.path("token_type").asText();

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Token Orange refuse: access_token absent");
        }
        if (!"Bearer".equalsIgnoreCase(tokenType)) {
            throw new IllegalStateException("Token Orange refuse: token_type inattendu " + tokenType);
        }

        long expiresIn = body.path("expires_in").asLong(3600);
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("[Orange OAuth] Token obtenu: tokenType={}, expiresIn={}, scopePresent={}",
                tokenType,
                expiresIn,
                body.hasNonNull("scope")
        );

        return accessToken;
    }

    private HttpResponse<String> requestOAuthAccessToken() throws IOException, InterruptedException {
        log.info("[Orange OAuth] Demande token M2M: url={}, contentType={}, accept={}",
                properties.tokenUrl(),
                FORM_URLENCODED,
                APPLICATION_JSON
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .setHeader("Authorization", basicAuthorizationHeader())
                .setHeader("Content-Type", FORM_URLENCODED)
                .setHeader("Accept", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(CLIENT_CREDENTIALS_BODY, StandardCharsets.UTF_8))
                .build();

        return httpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String basicAuthorizationHeader() {
        if (properties.basicAuthorization() != null && !properties.basicAuthorization().isBlank()) {
            String authorization = properties.basicAuthorization().trim();
            if (authorization.regionMatches(true, 0, "Basic ", 0, "Basic ".length())) {
                return authorization;
            }
            return "Basic " + authorization;
        }

        if (properties.clientId() == null || properties.clientSecret() == null
                || properties.clientId().isBlank() || properties.clientSecret().isBlank()) {
            throw new IllegalStateException("Identifiants Orange SMS non configures");
        }

        String credentials = properties.clientId() + ":" + properties.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
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
