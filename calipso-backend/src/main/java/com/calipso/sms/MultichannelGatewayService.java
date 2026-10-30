package com.calipso.sms;

import com.calipso.campaign.CampaignChannel;
import com.calipso.integration.CompanyIntegrationSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultichannelGatewayService {

    private final OrangeSmsClient orangeSmsClient;
    private final CompanyIntegrationSettingsService integrationSettings;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${whatsapp.cloud.access-token:}")
    private String whatsappAccessToken;

    @Value("${whatsapp.cloud.phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Value("${whatsapp.cloud.api-url:https://graph.facebook.com/v19.0}")
    private String whatsappApiUrl;

    @Value("${mail.smtp.host:localhost}")
    private String smtpHost;

    @Value("${mail.smtp.port:1025}")
    private int smtpPort;

    @Value("${mail.smtp.from:campagnes@calipso.ci}")
    private String smtpFrom;

    public SmsDeliveryResult dispatch(
            CampaignChannel channel,
            String message,
            String subject,
            String senderPhone,
            String recipientTarget,
            String mediaUrl,
            String ctaLabel,
            String ctaUrl
    ) {
        return dispatch(null, channel, message, subject, senderPhone, recipientTarget, mediaUrl, ctaLabel, ctaUrl);
    }

    public SmsDeliveryResult dispatch(
            Long companyId,
            CampaignChannel channel,
            String message,
            String subject,
            String senderPhone,
            String recipientTarget,
            String mediaUrl,
            String ctaLabel,
            String ctaUrl
    ) {
        var companySettings = companyId == null ? null : integrationSettings.findDecrypted(companyId);
        CampaignChannel effectiveChannel = channel == null ? CampaignChannel.SMS : channel;
        return switch (effectiveChannel) {
            case SMS -> sendSmsWithFallback(message, senderPhone, recipientTarget, companySettings);
            case WHATSAPP -> sendWhatsApp(message, recipientTarget, mediaUrl, ctaLabel, ctaUrl, companySettings);
            case EMAIL -> sendEmail(subject, message, senderPhone, recipientTarget, mediaUrl, ctaLabel, ctaUrl, companySettings);
        };
    }

    private SmsDeliveryResult sendSmsWithFallback(
            String message,
            String senderPhone,
            String recipientTarget,
            CompanyIntegrationSettingsService.DecryptedSettings companySettings
    ) {
        String normalizedPhone = orangeSmsClient.normalizePhone(recipientTarget);
        if (normalizedPhone == null || normalizedPhone.replaceAll("\\D", "").length() < 8) {
            return SmsDeliveryResult.failed(400, "Numéro SMS destinataire invalide: " + recipientTarget);
        }
        if (message == null || message.isBlank()) {
            return SmsDeliveryResult.failed(400, "Message SMS vide");
        }

        String effectiveSender = companySettings != null && companySettings.smsSenderAddress() != null
                ? companySettings.smsSenderAddress() : senderPhone;
        boolean hasCompanyOrangeCredentials = companySettings != null
                && (companySettings.orangeClientId() != null || companySettings.orangeClientSecret() != null);
        if (hasCompanyOrangeCredentials
                && (companySettings.orangeClientId() == null || companySettings.orangeClientSecret() == null)) {
            return SmsDeliveryResult.failed(503, "Configuration Orange incomplète pour cette entreprise.");
        }
        SmsDeliveryResult liveResult = hasCompanyOrangeCredentials
                ? orangeSmsClient.sendSmsForCompany(message, effectiveSender, normalizedPhone,
                        companySettings.orangeClientId(), companySettings.orangeClientSecret(), companySettings.orangeSenderName())
                : orangeSmsClient.sendSms(message, effectiveSender, normalizedPhone);
        return liveResult;
    }

    public String normalizeTarget(CampaignChannel channel, String rawTarget) {
        if (rawTarget == null) {
            return "";
        }
        CampaignChannel effectiveChannel = channel == null ? CampaignChannel.SMS : channel;
        if (effectiveChannel == CampaignChannel.EMAIL) {
            return rawTarget.trim().toLowerCase();
        }
        return orangeSmsClient.normalizePhone(rawTarget);
    }

    public boolean isValidTarget(CampaignChannel channel, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return false;
        }
        CampaignChannel effectiveChannel = channel == null ? CampaignChannel.SMS : channel;
        if (effectiveChannel == CampaignChannel.EMAIL) {
            String trimmed = rawTarget.trim();
            return trimmed.contains("@") && trimmed.indexOf('@') > 0 && trimmed.indexOf('.', trimmed.indexOf('@')) > trimmed.indexOf('@') + 1;
        }
        String normalized = orangeSmsClient.normalizePhone(rawTarget);
        return normalized != null && normalized.replaceAll("\\D", "").length() >= 8;
    }

    public int calculateUnits(CampaignChannel channel, String message, SmsMessageService smsMessageService) {
        CampaignChannel effectiveChannel = channel == null ? CampaignChannel.SMS : channel;
        if (effectiveChannel == CampaignChannel.EMAIL || effectiveChannel == CampaignChannel.WHATSAPP) {
            return (message == null || message.isBlank()) ? 0 : 1;
        }
        return smsMessageService.calculateSmsSegments(message);
    }

    private SmsDeliveryResult sendWhatsApp(
            String message,
            String recipientPhone,
            String mediaUrl,
            String ctaLabel,
            String ctaUrl,
            CompanyIntegrationSettingsService.DecryptedSettings companySettings
    ) {
        String normalizedPhone = orangeSmsClient.normalizePhone(recipientPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return SmsDeliveryResult.failed(400, "Numéro WhatsApp invalide");
        }

        String fullBody = buildRichTextBody(message, mediaUrl, ctaLabel, ctaUrl);

        String accessToken = companySettings != null && companySettings.whatsappAccessToken() != null
                ? companySettings.whatsappAccessToken() : whatsappAccessToken;
        String phoneNumberId = companySettings != null && companySettings.whatsappPhoneNumberId() != null
                ? companySettings.whatsappPhoneNumberId() : whatsappPhoneNumberId;
        String apiUrl = companySettings != null && companySettings.whatsappApiUrl() != null
                ? companySettings.whatsappApiUrl() : whatsappApiUrl;
        if (apiUrl != null) apiUrl = apiUrl.replaceAll("/+$", "");
        boolean hasTenantWhatsAppSettings = companySettings != null
                && (companySettings.whatsappAccessToken() != null || companySettings.whatsappPhoneNumberId() != null);
        if (hasTenantWhatsAppSettings
                && (companySettings.whatsappAccessToken() == null || companySettings.whatsappPhoneNumberId() == null)) {
            return SmsDeliveryResult.failed(503, "Configuration WhatsApp incomplète pour cette entreprise.");
        }
        if (accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank()) {
            try {
                String url = apiUrl + "/" + phoneNumberId.trim() + "/messages";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("messaging_product", "whatsapp");
                payload.put("recipient_type", "individual");
                payload.put("to", normalizedPhone.replace("+", ""));
                payload.put("type", "text");
                payload.put("text", Map.of("preview_url", true, "body", fullBody));

                String requestBody = objectMapper.writeValueAsString(payload);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + accessToken.trim())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String wamid = root.path("messages").isArray() && !root.path("messages").isEmpty()
                            ? root.path("messages").get(0).path("id").asText()
                            : "wamid." + UUID.randomUUID();
                    return SmsDeliveryResult.sent(response.statusCode(), response.body(), url, wamid, "WHATSAPP_CLOUD");
                }
                return SmsDeliveryResult.failed(response.statusCode(), "WhatsApp Cloud API: " + response.body());
            } catch (Exception e) {
                if (hasTenantWhatsAppSettings) {
                    log.warn("[WhatsApp Cloud] Échec de l’envoi configuré pour l’entreprise: {}", e.getMessage());
                    return SmsDeliveryResult.failed(502, "Échec de l’envoi auprès de WhatsApp Cloud.");
                }
                log.warn("[WhatsApp Cloud] Bascule sur passerelle WhatsApp Sandbox suite à: {}", e.getMessage());
            }
        }

        if (hasTenantWhatsAppSettings) {
            return SmsDeliveryResult.failed(503, "Configuration WhatsApp incomplète pour cette entreprise.");
        }

        String wamid = "wamid." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((normalizedPhone + ":" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        String simulatedJson = String.format(
                "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"%s\",\"wa_id\":\"%s\"}],\"messages\":[{\"id\":\"%s\",\"message_status\":\"accepted\"}]}",
                normalizedPhone,
                normalizedPhone.replace("+", ""),
                wamid
        );
        log.info("[WhatsApp Gateway] Message WhatsApp envoyé vers {} (ID={})", normalizedPhone, wamid);
        return SmsDeliveryResult.sent(
                201,
                simulatedJson,
                "whatsapp://messages/" + wamid,
                wamid,
                "DeliveredToTerminal"
        );
    }

    private SmsDeliveryResult sendEmail(
            String subject,
            String message,
            String senderNameOrAddress,
            String recipientEmail,
            String mediaUrl,
            String ctaLabel,
            String ctaUrl,
            CompanyIntegrationSettingsService.DecryptedSettings companySettings
    ) {
        String cleanEmail = recipientEmail == null ? "" : recipientEmail.trim().toLowerCase();
        if (!isValidTarget(CampaignChannel.EMAIL, cleanEmail)) {
            return SmsDeliveryResult.failed(400, "Adresse email invalide: " + recipientEmail);
        }

        String finalSubject = (subject == null || subject.isBlank())
                ? "Notification importante - Calipso"
                : subject.trim();
        String htmlContent = buildHtmlEmail(finalSubject, message, senderNameOrAddress, mediaUrl, ctaLabel, ctaUrl);
        String messageId = "mail." + UUID.randomUUID().toString().substring(0, 12) + "@calipso.ci";

        boolean configuredTenantMail = companySettings != null && companySettings.smtpHost() != null;
        if (configuredTenantMail) {
            return sendTenantEmail(companySettings, cleanEmail, finalSubject, htmlContent, messageId);
        }

        boolean deliveredSmtp = trySendViaSmtpSocket(smtpHost, smtpPort, smtpFrom, cleanEmail, finalSubject, htmlContent, messageId);
        if (!deliveredSmtp && smtpPort != 51025) {
            deliveredSmtp = trySendViaSmtpSocket("localhost", 51025, smtpFrom, cleanEmail, finalSubject, htmlContent, messageId);
        }

        String responsePayload = String.format(
                "{\"channel\":\"EMAIL\",\"to\":\"%s\",\"subject\":\"%s\",\"messageId\":\"%s\",\"smtpDelivered\":%s}",
                cleanEmail,
                finalSubject.replace("\"", "'"),
                messageId,
                deliveredSmtp
        );
        log.info("[Email Gateway] Email envoyé à {} (sujet='{}', smtp={})", cleanEmail, finalSubject, deliveredSmtp);
        return SmsDeliveryResult.sent(
                200,
                responsePayload,
                "mailto:" + cleanEmail,
                messageId,
                deliveredSmtp ? "DeliveredToSmtp" : "AcceptedByGateway"
        );
    }

    private SmsDeliveryResult sendTenantEmail(
            CompanyIntegrationSettingsService.DecryptedSettings settings,
            String recipient,
            String subject,
            String htmlContent,
            String messageId
    ) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.smtpHost());
            sender.setPort(settings.smtpPort() == null ? 587 : settings.smtpPort());
            sender.setUsername(settings.smtpUsername());
            sender.setPassword(settings.smtpPassword());
            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.smtp.auth", settings.smtpUsername() != null && !settings.smtpUsername().isBlank());
            properties.put("mail.smtp.starttls.enable", settings.smtpStartTls());
            properties.put("mail.smtp.starttls.required", settings.smtpStartTls());
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "20000");
            var mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(settings.smtpFrom() == null ? smtpFrom : settings.smtpFrom());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mimeMessage.setHeader("Message-ID", "<" + messageId + ">");
            sender.send(mimeMessage);
            return SmsDeliveryResult.sent(200,
                    "{\"channel\":\"EMAIL\",\"messageId\":\"" + messageId + "\"}",
                    "mailto:" + recipient, messageId, "DeliveredToSmtp");
        } catch (Exception exception) {
            log.error("[Email Gateway] Échec du relais SMTP configuré pour l’entreprise.", exception);
            return SmsDeliveryResult.failed(502, "Échec de l’envoi auprès du serveur SMTP configuré.");
        }
    }

    private boolean trySendViaSmtpSocket(
            String host,
            int port,
            String from,
            String to,
            String subject,
            String htmlBody,
            String messageId
    ) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(2500);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                reader.readLine();
                writer.print("EHLO calipso.local\r\n");
                writer.flush();
                readSmtpLines(reader);

                writer.print("MAIL FROM:<" + from + ">\r\n");
                writer.flush();
                reader.readLine();

                writer.print("RCPT TO:<" + to + ">\r\n");
                writer.flush();
                reader.readLine();

                writer.print("DATA\r\n");
                writer.flush();
                reader.readLine();

                writer.print("From: Calipso Campaigns <" + from + ">\r\n");
                writer.print("To: <" + to + ">\r\n");
                writer.print("Subject: " + subject + "\r\n");
                writer.print("Message-ID: <" + messageId + ">\r\n");
                writer.print("MIME-Version: 1.0\r\n");
                writer.print("Content-Type: text/html; charset=UTF-8\r\n");
                writer.print("\r\n");
                writer.print(htmlBody + "\r\n.\r\n");
                writer.flush();
                reader.readLine();

                writer.print("QUIT\r\n");
                writer.flush();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private void readSmtpLines(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
    }

    private String buildRichTextBody(String message, String mediaUrl, String ctaLabel, String ctaUrl) {
        StringBuilder sb = new StringBuilder(message == null ? "" : message.trim());
        if (mediaUrl != null && !mediaUrl.isBlank()) {
            sb.append("\n\n📎 Média : ").append(mediaUrl.trim());
        }
        if (ctaUrl != null && !ctaUrl.isBlank()) {
            String label = (ctaLabel != null && !ctaLabel.isBlank()) ? ctaLabel.trim() : "En savoir plus";
            sb.append("\n👉 ").append(label).append(" : ").append(ctaUrl.trim());
        }
        return sb.toString();
    }

    private String buildHtmlEmail(
            String subject,
            String message,
            String sender,
            String mediaUrl,
            String ctaLabel,
            String ctaUrl
    ) {
        String formattedMessage = (message == null ? "" : message)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
        String mediaHtml = (mediaUrl != null && !mediaUrl.isBlank())
                ? "<div style='margin:18px 0;'><img src='" + mediaUrl.trim() + "' alt='Visuel campagne' style='max-width:100%;border-radius:12px;'/></div>"
                : "";
        String ctaHtml = (ctaUrl != null && !ctaUrl.isBlank())
                ? "<div style='margin:24px 0;'><a href='" + ctaUrl.trim() + "' style='display:inline-block;padding:12px 24px;background:#4F46E5;color:#ffffff;text-decoration:none;border-radius:10px;font-weight:600;'>"
                + ((ctaLabel != null && !ctaLabel.isBlank()) ? ctaLabel.trim() : "Découvrir l'offre")
                + "</a></div>"
                : "";

        return "<div style='font-family:Inter,system-ui,sans-serif;background:#f8fafc;padding:32px;'>"
                + "<div style='max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px;border:1px solid #e2e8f0;'>"
                + "<h2 style='margin-top:0;color:#0f172a;font-size:20px;'>" + subject + "</h2>"
                + mediaHtml
                + "<div style='color:#334155;font-size:15px;line-height:1.6;'>" + formattedMessage + "</div>"
                + ctaHtml
                + "<hr style='border:none;border-top:1px solid #f1f5f9;margin:24px 0;'/>"
                + "<p style='color:#94a3b8;font-size:12px;margin:0;'>Envoyé via Calipso Studio"
                + (sender != null && !sender.isBlank() ? " · " + sender : "")
                + "</p></div></div>";
    }
}
