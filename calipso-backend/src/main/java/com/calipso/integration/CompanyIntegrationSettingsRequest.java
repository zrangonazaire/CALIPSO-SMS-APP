package com.calipso.integration;

public record CompanyIntegrationSettingsRequest(
        String smsSenderAddress,
        String orangeClientId,
        String orangeClientSecret,
        String whatsappPhoneNumberId,
        String whatsappAccessToken,
        String whatsappApiUrl,
        String smtpHost,
        Integer smtpPort,
        String smtpFrom,
        String smtpUsername,
        String smtpPassword,
        Boolean smtpStartTls,
        String orangeSenderName
) {
}
