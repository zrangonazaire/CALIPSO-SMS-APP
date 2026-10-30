package com.calipso.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MobilePaymentGatewayService {

    private static final DateTimeFormatter REF_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${payment.gateway.provider:CALIPSO_PAY_CI}")
    private String configuredProvider;

    @Value("${payment.gateway.cinetpay.api-key:}")
    private String cinetpayApiKey;

    @Value("${payment.gateway.cinetpay.site-id:}")
    private String cinetpaySiteId;

    @Value("${payment.gateway.cinetpay.notify-url:}")
    private String cinetpayNotifyUrl;

    @Value("${payment.gateway.cinetpay.return-url:}")
    private String cinetpayReturnUrl;

    @Value("${payment.gateway.wave.api-key:}")
    private String waveApiKey;

    @Value("${payment.gateway.allow-local-simulation:false}")
    private boolean allowLocalSimulation;

    public record PaymentChargeResult(
            String reference,
            PaymentMethod paymentMethod,
            PaymentStatus status,
            String normalizedPhone,
            String providerName,
            String checkoutUrl,
            String providerMessage
    ) {
    }

    public PaymentChargeResult processPayment(
            Long companyId,
            String companyName,
            int amountFcfa,
            int smsUnits,
            PaymentMethod method,
            String rawPhone,
            String description
    ) {
        PaymentMethod effectiveMethod = method == null ? PaymentMethod.WAVE_CI : method;
        String reference = generateReference(effectiveMethod);

        String normalizedPhone = null;
        if (effectiveMethod != PaymentMethod.CARD && effectiveMethod != PaymentMethod.SYSTEM) {
            normalizedPhone = normalizeAndValidateCiPhone(rawPhone, effectiveMethod);
        }

        // Si CinetPay est configuré avec clé API et site ID réels
        if (cinetpayApiKey != null && !cinetpayApiKey.isBlank()
                && cinetpaySiteId != null && !cinetpaySiteId.isBlank()) {
            try {
                return initiateCinetPayCheckout(
                        reference,
                        amountFcfa,
                        effectiveMethod,
                        normalizedPhone,
                        companyName,
                        description
                );
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Le prestataire CinetPay n’a pas confirmé la création du paiement. Aucun forfait activé.");
            }
        }

        // Si Wave Business API directe est configurée
        if (effectiveMethod == PaymentMethod.WAVE_CI && waveApiKey != null && !waveApiKey.isBlank()) {
            try {
                return initiateWaveCheckout(reference, amountFcfa, normalizedPhone, description);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Le prestataire Wave n’a pas confirmé la création du paiement. Aucun forfait activé.");
            }
        }

        if (!allowLocalSimulation) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Aucun prestataire de paiement disponible pour ce moyen de paiement. Contactez l’administrateur pour configurer les encaissements.");
        }
        String providerName = (configuredProvider == null || configuredProvider.isBlank())
                ? "CALIPSO_PAY_CI"
                : configuredProvider;

        log.info(
                "[MobilePayment CI] Paiement validé : ref={}, company={} ({}), méthode={}, tel={}, montant={} FCFA ({} crédits)",
                reference,
                companyName,
                companyId,
                effectiveMethod.name(),
                normalizedPhone != null ? normalizedPhone : "CARTE",
                amountFcfa,
                smsUnits
        );

        return new PaymentChargeResult(
                reference,
                effectiveMethod,
                PaymentStatus.COMPLETED,
                normalizedPhone,
                providerName,
                null,
                "Paiement " + effectiveMethod.getLabel() + " confirmé"
        );
    }

    public String normalizeAndValidateCiPhone(String rawPhone, PaymentMethod method) {
        if (rawPhone == null || rawPhone.isBlank()) {
            // Si non fourni par un appel API legacy, utilise un numéro marchand CI par défaut
            return "+2250700000000";
        }

        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.startsWith("00225")) {
            digits = digits.substring(5);
        } else if (digits.startsWith("225") && digits.length() == 13) {
            digits = digits.substring(3);
        }

        // Format national Côte d'Ivoire à 10 chiffres (01, 05, 07) ou ancien format 8 chiffres
        if (digits.length() == 8) {
            digits = "07" + digits;
        }

        if (digits.length() != 10 || !(digits.startsWith("01") || digits.startsWith("05") || digits.startsWith("07"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Numéro Mobile Money ivoirien invalide. Format attendu : +225 07/05/01 XX XX XX XX (10 chiffres)."
            );
        }

        return "+225" + digits;
    }

    public String generateReference(PaymentMethod method) {
        String prefix = switch (method == null ? PaymentMethod.WAVE_CI : method) {
            case WAVE_CI -> "WAV";
            case ORANGE_MONEY_CI -> "OM";
            case MTN_MOMO_CI -> "MTN";
            case MOOV_MONEY_CI -> "MOOV";
            case CARD -> "CRD";
            case SYSTEM -> "SYS";
        };
        String datePart = LocalDate.now().format(REF_DATE_FMT);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CAL-" + prefix + "-" + datePart + "-" + randomPart;
    }

    private PaymentChargeResult initiateCinetPayCheckout(
            String reference,
            int amountFcfa,
            PaymentMethod method,
            String normalizedPhone,
            String companyName,
            String description
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apikey", cinetpayApiKey.trim());
        payload.put("site_id", cinetpaySiteId.trim());
        payload.put("transaction_id", reference);
        payload.put("amount", amountFcfa);
        payload.put("currency", "XOF");
        payload.put("description", description);
        payload.put("channels", "ALL");
        payload.put("customer_name", companyName);
        if (normalizedPhone != null) {
            payload.put("customer_phone_number", normalizedPhone);
        }
        if (cinetpayNotifyUrl != null && !cinetpayNotifyUrl.isBlank()) {
            payload.put("notify_url", cinetpayNotifyUrl.trim());
        }
        if (cinetpayReturnUrl != null && !cinetpayReturnUrl.isBlank()) {
            payload.put("return_url", cinetpayReturnUrl.trim());
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api-checkout.cinetpay.com/v2/payment"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        return pendingCheckout(response.statusCode(), root.path("data").path("payment_url").asText(null),
                reference, method, normalizedPhone, "CINETPAY_CI", "Paiement CinetPay initié");
    }

    private PaymentChargeResult initiateWaveCheckout(
            String reference,
            int amountFcfa,
            String normalizedPhone,
            String description
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", String.valueOf(amountFcfa));
        payload.put("currency", "XOF");
        payload.put("client_reference", reference);
        payload.put("error_url", "https://calipso.ci/subscription?status=error");
        payload.put("success_url", "https://calipso.ci/subscription?status=success");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.wave.com/v1/checkout/sessions"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + waveApiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        return pendingCheckout(response.statusCode(), root.path("wave_launch_url").asText(null),
                reference, PaymentMethod.WAVE_CI, normalizedPhone, "WAVE_BUSINESS_CI", description);
    }

    PaymentChargeResult pendingCheckout(int httpStatus, String url, String reference, PaymentMethod method,
            String phone, String provider, String message) {
        if (httpStatus < 200 || httpStatus >= 300 || url == null || !url.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Le prestataire n’a pas créé de paiement valide.");
        }
        return new PaymentChargeResult(reference, method, PaymentStatus.PENDING, phone, provider, url, message);
    }
}
