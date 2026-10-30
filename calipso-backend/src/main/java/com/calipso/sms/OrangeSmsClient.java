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
import java.util.LinkedHashMap;
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

    private static final String DEFAULT_ORANGE_CIV_SENDER_ADDRESS = "+2250000";

    private final OrangeSmsProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CompanyToken> companyTokens = new java.util.HashMap<>();
    private record CompanyToken(String value, Instant expiresAt) { }

    private String companyTokenKey(String basic) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(basic.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private synchronized String companyAccessToken(String clientId, String clientSecret, boolean refresh)
            throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((clientId.trim() + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String cacheKey = companyTokenKey(basic);
        CompanyToken cached = companyTokens.get(cacheKey);
        if (!refresh && cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(TOKEN_EXPIRY_SAFETY_SECONDS))) return cached.value();
        companyTokens.remove(cacheKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", FORM_URLENCODED).header("Accept", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(CLIENT_CREDENTIALS_BODY)).build();
        var response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Authentification Orange refusée : HTTP " + response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        String token = body.path("access_token").asText("");
        if (token.isBlank()) throw new IOException("Orange n’a pas retourné de jeton d’accès.");
        companyTokens.put(cacheKey, new CompanyToken(token, Instant.now().plusSeconds(Math.max(0, body.path("expires_in").asLong(3600)))));
        return token;
    }

    public record ConnectionStatus(boolean authenticated, String tokenType, Instant tokenExpiresAt,
            boolean contractsAccessible, int availableUnits, List<OrangeSmsContract> contracts, String message) {}

    // Validates authentication and reads contracts only: no SMS, purchase or wallet update.
    public synchronized ConnectionStatus checkCompanyConnection(String clientId, String clientSecret)
            throws IOException, InterruptedException {
        String token = companyAccessToken(clientId, clientSecret, false);
        HttpResponse<String> response = null;
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                var request = HttpRequest.newBuilder(URI.create(properties.contractsUrl()))
                        .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                        .header("Authorization", "Bearer " + token).header("Accept", APPLICATION_JSON).GET().build();
                response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 401 || attempt == 1) break;
                token = companyAccessToken(clientId, clientSecret, true);
            }
            var cached = companyTokens.get(companyTokenKey(Base64.getEncoder().encodeToString(
                    (clientId.trim() + ":" + clientSecret).getBytes(StandardCharsets.UTF_8))));
            if (response.statusCode() != 200) {
                return new ConnectionStatus(true, "Bearer", cached.expiresAt(), false, 0, List.of(),
                        "Token obtenu. Lecture du contrat Orange refusée (HTTP " + response.statusCode() + ").");
            }
            var contracts = parseSmsContracts(objectMapper.readTree(response.body()));
            int units = contracts.stream().filter(this::isAvailableContract)
                    .filter(c -> c.expirationDate() != null && !isExpired(c.expirationDate()))
                    .mapToInt(c -> Math.max(0, c.availableUnits() == null ? 0 : c.availableUnits())).sum();
            return new ConnectionStatus(true, "Bearer", cached.expiresAt(), true, units, contracts,
                    units > 0 ? "Authentification et forfait Orange vérifiés." : "Authentification réussie. Aucun crédit disponible sur un contrat non expiré.");
        } catch (IOException exception) {
            var cached = companyTokens.get(companyTokenKey(Base64.getEncoder().encodeToString(
                    (clientId.trim() + ":" + clientSecret).getBytes(StandardCharsets.UTF_8))));
            if (cached == null) throw exception;
            return new ConnectionStatus(true, "Bearer", cached.expiresAt(), false, 0, List.of(),
                    "Token obtenu. Impossible de lire les contrats Orange actuellement.");
        }
    }

    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;
    private String deliveryReceiptSubscriptionResourceUrl;
    private String deliveryReceiptSubscriptionId;

    public SmsDeliveryResult sendSms(String message, String senderPhone, String recipientPhone) {
        try {
            log.info(
                    "[Orange SMS] Debut sendSms: message={}, senderPhone={}, recipientPhone={}",
                    message,
                    senderPhone,
                    recipientPhone
            );

            if (message == null || message.isBlank()) {
                return SmsDeliveryResult.failed(0, "Message SMS vide");
            }

            if (recipientPhone == null || recipientPhone.isBlank()) {
                return SmsDeliveryResult.failed(0, "Numero destinataire vide");
            }

            String senderAddress = configuredSenderAddress();
            String recipientAddress = normalizeRecipientPhone(recipientPhone);

            if (!recipientAddress.matches("\\+225[0-9]{10}")) {
                return SmsDeliveryResult.failed(
                        0,
                        "Numero destinataire invalide apres normalisation: " + recipientAddress
                );
            }

            String token = getAccessToken();
            tryEnsureDeliveryReceiptSubscription(senderAddress, token);

            String url = buildOutboundUrl(senderAddress);
            Map<String, Object> outboundSMSMessageRequest = new LinkedHashMap<>();
            outboundSMSMessageRequest.put("address", "tel:" + recipientAddress);
            outboundSMSMessageRequest.put("senderAddress", "tel:" + senderAddress);

            if (properties.senderName() != null && !properties.senderName().isBlank()) {
                log.warn(
                        "[Orange SMS] senderName configure: {}. Verifier le whitelisting Orange si le SMS "
                                + "est accepte mais non recu.",
                        properties.senderName().trim()
                );
                outboundSMSMessageRequest.put("senderName", properties.senderName().trim());
            }

            outboundSMSMessageRequest.put(
                    "outboundSMSTextMessage",
                    Map.of("message", message)
            );

            Map<String, Object> payload = Map.of(
                    "outboundSMSMessageRequest",
                    outboundSMSMessageRequest
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            log.info("[Orange SMS] senderAddress final={}", senderAddress);
            log.info("[Orange SMS] recipientAddress final={}", recipientAddress);
            log.info("[Orange SMS] url final={}", url);
            log.info("[Orange SMS] payload envoye a Orange={}", requestBody);

            HttpResponse<String> response = sendSmsRequestWithRetry(url, requestBody, token);

            log.info(
                    "[Orange SMS] Reponse finale Orange: statusCode={}, location={}, body={}",
                    response.statusCode(),
                    response.headers().firstValue("Location").orElse(""),
                    response.body()
            );

            if (response.statusCode() == 201) {
                String providerResourceUrl = extractProviderResourceUrl(response);
                String providerResourceId = extractProviderResourceId(providerResourceUrl);

                log.info(
                        "[Orange SMS] SMS accepte par Orange: providerResourceId={}, providerResourceUrl={}",
                        providerResourceId,
                        providerResourceUrl
                );

                return SmsDeliveryResult.sent(
                        response.statusCode(),
                        response.body(),
                        providerResourceUrl,
                        providerResourceId,
                        null
                );
            }

            if (response.statusCode() == 401) {
                accessToken = null;
                tokenExpiresAt = Instant.MIN;

                return SmsDeliveryResult.failed(
                        response.statusCode(),
                        "Token Orange invalide ou expire: " + response.body()
                );
            }

            return SmsDeliveryResult.failed(response.statusCode(), response.body());
        } catch (Exception exception) {
            log.error("[Orange SMS] Erreur pendant l'envoi SMS", exception);
            return SmsDeliveryResult.failed(0, exception.getMessage());
        }
    }

    public SmsDeliveryResult sendSmsForCompany(
            String message,
            String senderPhone,
            String recipientPhone,
            String clientId,
            String clientSecret,
            String senderName
    ) {
        try {
            String senderAddress = DEFAULT_ORANGE_CIV_SENDER_ADDRESS;
            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                return SmsDeliveryResult.failed(400, "Identifiants Orange incomplets.");
            }
            if (senderName != null && !senderName.isBlank() && !senderName.trim().matches("[A-Za-z0-9 ]{1,11}")) {
                return SmsDeliveryResult.failed(400, "Nom d’expéditeur Orange invalide.");
            }
            String recipientAddress = normalizeRecipientPhone(recipientPhone);
            if (message == null || message.isBlank() || recipientAddress == null || !recipientAddress.matches("\\+225[0-9]{10}")) {
                return SmsDeliveryResult.failed(400, "Message ou numéro destinataire SMS invalide.");
            }

            String token = companyAccessToken(clientId, clientSecret, false);

            Map<String, Object> outboundRequest = new LinkedHashMap<>();
            outboundRequest.put("address", "tel:" + recipientAddress);
            outboundRequest.put("senderAddress", "tel:" + senderAddress);
            if (senderName != null && !senderName.isBlank()) outboundRequest.put("senderName", senderName.trim());
            outboundRequest.put("outboundSMSTextMessage", Map.of("message", message));
            String requestBody = objectMapper.writeValueAsString(Map.of("outboundSMSMessageRequest", outboundRequest));
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpRequest sendRequest = HttpRequest.newBuilder(URI.create(buildOutboundUrl(senderAddress)))
                        .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", APPLICATION_JSON).header("Accept", APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build();
                response = httpClient().send(sendRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 401 || attempt == 1) break;
                token = companyAccessToken(clientId, clientSecret, true);
            }
            if (response.statusCode() == 201) {
                String resourceUrl = extractProviderResourceUrl(response);
                return SmsDeliveryResult.sent(response.statusCode(), response.body(), resourceUrl,
                        extractProviderResourceId(resourceUrl), null);
            }
            return SmsDeliveryResult.failed(response.statusCode(), response.body());
        } catch (Exception exception) {
            log.error("[Orange SMS] Échec d’envoi avec les identifiants propres à l’entreprise.", exception);
            return SmsDeliveryResult.failed(0, "Échec de l’envoi auprès du fournisseur Orange.");
        }
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
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
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

    private void tryEnsureDeliveryReceiptSubscription(String senderAddress, String token) {
        if (properties.deliveryReceiptUrl() == null || properties.deliveryReceiptUrl().isBlank()) {
            return;
        }

        try {
            ensureDeliveryReceiptSubscription(senderAddress, token);
        } catch (Exception exception) {
            log.warn(
                    "[Orange SMS DR] Souscription DR non disponible, envoi SMS conserve: {}",
                    exception.getMessage()
            );
        }
    }

    private synchronized void ensureDeliveryReceiptSubscription(String senderAddress, String token)
            throws IOException, InterruptedException {

        if (deliveryReceiptSubscriptionId != null && !deliveryReceiptSubscriptionId.isBlank()) {
            return;
        }

        if (properties.deliveryReceiptSubscriptionId() != null
                && !properties.deliveryReceiptSubscriptionId().isBlank()) {
            deliveryReceiptSubscriptionId = properties.deliveryReceiptSubscriptionId().trim();
            log.info("[Orange SMS DR] Souscription DR configuree: id={}", deliveryReceiptSubscriptionId);
            return;
        }

        OrangeSmsDeliveryReceiptSubscription subscription =
                createDeliveryReceiptSubscription(senderAddress, token);

        deliveryReceiptSubscriptionResourceUrl = subscription.resourceUrl();
        deliveryReceiptSubscriptionId = subscription.subscriptionId();
    }

    public OrangeSmsDeliveryReceiptSubscription createDeliveryReceiptSubscription()
            throws IOException, InterruptedException {
        return createDeliveryReceiptSubscription(configuredSenderAddress(), getAccessToken());
    }

    private OrangeSmsDeliveryReceiptSubscription createDeliveryReceiptSubscription(
            String senderAddress,
            String token
    ) throws IOException, InterruptedException {

        if (properties.deliveryReceiptUrl() == null || properties.deliveryReceiptUrl().isBlank()) {
            throw new IllegalStateException("URL de notification Orange DR non configuree");
        }

        String url = buildDeliveryReceiptSubscriptionUrl(senderAddress);
        Map<String, Object> payload = Map.of(
                "deliveryReceiptSubscription",
                Map.of(
                        "callbackReference",
                        Map.of("notifyURL", properties.deliveryReceiptUrl().trim())
                )
        );
        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Content-Type", APPLICATION_JSON)
                .setHeader("Accept", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        log.info(
                "[Orange SMS DR] Reponse souscription: statusCode={}, location={}, body={}",
                response.statusCode(),
                response.headers().firstValue("Location").orElse(""),
                response.body()
        );

        if (response.statusCode() == 401) {
            accessToken = null;
            tokenExpiresAt = Instant.MIN;
        }

        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                    "Souscription Orange DR refusee: HTTP " + response.statusCode() + " - " + response.body()
            );
        }

        String resourceUrl = extractDeliveryReceiptSubscriptionResourceUrl(response);
        String subscriptionId = extractProviderResourceId(resourceUrl);

        log.info(
                "[Orange SMS DR] Souscription creee: subscriptionId={}, resourceUrl={}",
                subscriptionId,
                resourceUrl
        );

        return new OrangeSmsDeliveryReceiptSubscription(resourceUrl, subscriptionId, response.body());
    }

    public OrangeSmsDeliveryReceiptSubscription getDeliveryReceiptSubscription(String subscriptionId)
            throws IOException, InterruptedException {

        String token = getAccessToken();
        String url = buildDeliveryReceiptSubscriptionUrl(configuredSenderAddress())
                + "/" + URLEncoder.encode(subscriptionId, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("Accept", APPLICATION_JSON)
                .GET()
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Lecture souscription Orange DR refusee: HTTP "
                            + response.statusCode() + " - " + response.body()
            );
        }

        String resourceUrl = extractDeliveryReceiptSubscriptionResourceUrl(response);
        return new OrangeSmsDeliveryReceiptSubscription(
                resourceUrl,
                extractProviderResourceId(resourceUrl),
                response.body()
        );
    }

    public void deleteDeliveryReceiptSubscription(String subscriptionId)
            throws IOException, InterruptedException {

        String token = getAccessToken();
        String url = buildDeliveryReceiptSubscriptionUrl(configuredSenderAddress())
                + "/" + URLEncoder.encode(subscriptionId, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .setHeader("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new IllegalStateException(
                    "Suppression souscription Orange DR refusee: HTTP "
                            + response.statusCode() + " - " + response.body()
            );
        }

        if (subscriptionId.equals(deliveryReceiptSubscriptionId)) {
            deliveryReceiptSubscriptionId = null;
            deliveryReceiptSubscriptionResourceUrl = null;
        }
    }

    public int fetchAvailableSmsUnits() {
        return fetchSmsContracts().stream()
                .filter(this::isAvailableContract)
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

            HttpResponse<String> response = httpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            log.info(
                    "[Orange SMS] Reponse contrats: statusCode={}, body={}",
                    response.statusCode(),
                    response.body()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Solde Orange refuse: HTTP " + response.statusCode() + " - " + response.body()
                );
            }

            List<OrangeSmsContract> results = parseSmsContracts(objectMapper.readTree(response.body()));
            int availableUnits = results.stream()
                    .filter(this::isAvailableContract)
                    .mapToInt(contract -> contract.availableUnits() == null ? 0 : contract.availableUnits())
                    .sum();

            log.info(
                    "[Orange SMS] Contrats Orange utilisables: {}, solde disponible: {} unite(s)",
                    results.stream().filter(this::isAvailableContract).count(),
                    availableUnits
            );

            return results;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de recuperer le solde Orange SMS: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<OrangeSmsContract> parseSmsContracts(JsonNode body) {
        List<OrangeSmsContract> results = new ArrayList<>();

        if (body == null || body.isMissingNode() || body.isNull()) {
            return results;
        }

        if (body.isArray()) {
            parseLegacyContracts(body, results);
            return results;
        }

        parsePartnerContracts(body.path("purchaseOrders"), results);
        parsePartnerContracts(body.path("partnerContracts"), results);
        return results;
    }

    private void parseLegacyContracts(JsonNode contracts, List<OrangeSmsContract> results) {
        for (JsonNode contract : contracts) {
            results.add(new OrangeSmsContract(
                    textOrNull(contract.path("id")),
                    textOrNull(contract.path("type")),
                    textOrNull(contract.path("country")),
                    textOrNull(contract.path("offerName")),
                    intOrNull(contract.path("availableUnits")),
                    intOrNull(contract.path("requestedUnits")),
                    textOrNull(contract.path("status")),
                    textOrNull(contract.path("expirationDate")),
                    textOrNull(contract.path("creationDate")),
                    textOrNull(contract.path("lastUpdateDate"))
            ));
        }
    }

    private void parsePartnerContracts(JsonNode partnerContracts, List<OrangeSmsContract> results) {
        if (partnerContracts == null || !partnerContracts.isArray()) {
            return;
        }

        for (JsonNode partnerContract : partnerContracts) {
            JsonNode contracts = partnerContract.path("contracts");
            if (!contracts.isArray()) {
                continue;
            }

            for (JsonNode contract : contracts) {
                String parentService = textOrNull(contract.path("service"));
                String parentDescription = textOrNull(contract.path("contractDescription"));
                JsonNode serviceContracts = contract.path("serviceContracts");

                if (!serviceContracts.isArray()) {
                    continue;
                }

                for (JsonNode serviceContract : serviceContracts) {
                    results.add(new OrangeSmsContract(
                            textOrNull(serviceContract.path("contractId")),
                            firstNotBlank(textOrNull(serviceContract.path("service")), parentService),
                            textOrNull(serviceContract.path("country")),
                            firstNotBlank(textOrNull(serviceContract.path("scDescription")), parentDescription),
                            intOrNull(serviceContract.path("availableUnits")),
                            null,
                            deriveContractStatus(serviceContract),
                            textOrNull(serviceContract.path("expires")),
                            null,
                            null
                    ));
                }
            }
        }
    }

    private boolean isAvailableContract(OrangeSmsContract contract) {
        if (contract.availableUnits() == null) {
            return false;
        }

        return contract.status() == null
                || contract.status().isBlank()
                || "ACTIVE".equalsIgnoreCase(contract.status());
    }

    private String deriveContractStatus(JsonNode serviceContract) {
        String status = textOrNull(serviceContract.path("status"));
        if (status != null) {
            return status;
        }

        String expires = textOrNull(serviceContract.path("expires"));
        if (expires != null && isExpired(expires)) {
            return "EXPIRED";
        }

        return "ACTIVE";
    }

    private boolean isExpired(String expires) {
        try {
            return Instant.parse(expires).isBefore(Instant.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null
                && Instant.now().isBefore(tokenExpiresAt.minusSeconds(TOKEN_EXPIRY_SAFETY_SECONDS))) {
            return accessToken;
        }

        HttpResponse<String> response = requestOAuthAccessToken();

        log.info(
                "[Orange OAuth] Reponse token: statusCode={}, body={}",
                response.statusCode(),
                response.body()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Token Orange refuse: HTTP " + response.statusCode() + " - " + response.body()
            );
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

        log.info(
                "[Orange OAuth] Token obtenu: tokenType={}, expiresIn={}, scopePresent={}",
                tokenType,
                expiresIn,
                body.hasNonNull("scope")
        );

        return accessToken;
    }

    private HttpResponse<String> requestOAuthAccessToken()
            throws IOException, InterruptedException {

        log.info(
                "[Orange OAuth] Demande token M2M: url={}, contentType={}, accept={}",
                properties.tokenUrl(),
                FORM_URLENCODED,
                APPLICATION_JSON
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .setHeader("Authorization", basicAuthorizationHeader())
                .setHeader("Content-Type", FORM_URLENCODED)
                .setHeader("Accept", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(
                        CLIENT_CREDENTIALS_BODY,
                        StandardCharsets.UTF_8
                ))
                .build();

        return httpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String basicAuthorizationHeader() {
        if (properties.basicAuthorization() != null
                && !properties.basicAuthorization().isBlank()) {

            String authorization = properties.basicAuthorization().trim();

            if (authorization.regionMatches(
                    true,
                    0,
                    "Basic ",
                    0,
                    "Basic ".length()
            )) {
                return authorization;
            }

            return "Basic " + authorization;
        }

        if (properties.clientId() == null || properties.clientId().isBlank()
                || properties.clientSecret() == null || properties.clientSecret().isBlank()) {

            throw new IllegalStateException("Identifiants Orange SMS non configures");
        }

        String credentials = properties.clientId() + ":" + properties.clientSecret();

        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String buildOutboundUrl(String senderAddress) {
        return properties.outboundUrl()
                .replace("{senderAddress}", encodeSenderAddress(senderAddress));
    }

    private String buildDeliveryReceiptSubscriptionUrl(String senderAddress) {
        return properties.subscriptionUrl()
                .replace("{senderAddress}", encodeSenderAddress(senderAddress));
    }

    private String encodeSenderAddress(String senderAddress) {
        return URLEncoder.encode("tel:" + senderAddress, StandardCharsets.UTF_8);
    }

    private String extractProviderResourceUrl(HttpResponse<String> response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode body = objectMapper.readTree(response.body());

                String resourceUrl = textOrNull(body.path("outboundSMSMessageRequest").path("resourceURL"));
                if (resourceUrl != null) {
                    return resourceUrl;
                }
            }
        } catch (Exception exception) {
            log.warn(
                    "[Orange SMS] Impossible de lire resourceURL dans la reponse Orange: {}",
                    exception.getMessage()
            );
        }

        return response.headers().firstValue("Location").orElse(null);
    }

    private String extractDeliveryReceiptSubscriptionResourceUrl(HttpResponse<String> response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode body = objectMapper.readTree(response.body());

                String resourceUrl = textOrNull(
                        body.path("deliveryReceiptSubscription").path("resourceURL")
                );
                if (resourceUrl != null) {
                    return resourceUrl;
                }
            }
        } catch (Exception exception) {
            log.warn(
                    "[Orange SMS DR] Impossible de lire resourceURL dans la reponse Orange: {}",
                    exception.getMessage()
            );
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

    public String normalizeRecipientPhone(String phone) {
        if (phone == null) {
            return "";
        }

        String cleaned = phone
                .replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .replace("(", "")
                .replace(")", "")
                .trim();

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }

        String defaultCountryCode = properties.defaultCountryCode();
        if (defaultCountryCode == null || defaultCountryCode.isBlank()) {
            defaultCountryCode = "225";
        }

        if (cleaned.startsWith(defaultCountryCode)) {
            return "+" + cleaned;
        }

        if (cleaned.startsWith("01")
                || cleaned.startsWith("05")
                || cleaned.startsWith("07")) {
            return "+" + defaultCountryCode + cleaned;
        }

        return cleaned;
    }

    public String normalizePhone(String phone) {
        return normalizeRecipientPhone(phone);
    }

    private String configuredSenderAddress() {
        String senderAddress = properties.defaultSenderAddress();
        if (senderAddress == null || senderAddress.isBlank()) {
            return DEFAULT_ORANGE_CIV_SENDER_ADDRESS;
        }

        String cleaned = senderAddress
                .replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .replace("(", "")
                .replace(")", "")
                .trim();

        if (cleaned.regionMatches(true, 0, "tel:", 0, "tel:".length())) {
            cleaned = cleaned.substring("tel:".length());
        }

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }

        // Short codes (≤ 7 digits) are used as-is without country code prefix
        if (cleaned.matches("\\d{1,7}")) {
            return cleaned;
        }

        String defaultCountryCode = properties.defaultCountryCode();
        if (defaultCountryCode == null || defaultCountryCode.isBlank()) {
            defaultCountryCode = "225";
        }

        if (cleaned.startsWith(defaultCountryCode)) {
            return "+" + cleaned;
        }

        return "+" + defaultCountryCode + cleaned;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        return node.asInt();
    }

    private String firstNotBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
    }
}
