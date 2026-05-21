package com.calipso.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms.orange")
public record OrangeSmsProperties(
        String tokenUrl,
        String outboundUrl,
        String basicAuthorization,
        String clientId,
        String clientSecret,
        String defaultSenderAddress,
        String defaultCountryCode,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds
) {
    public OrangeSmsProperties {
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://api.orange.com/oauth/v3/token";
        }
        if (outboundUrl == null || outboundUrl.isBlank()) {
            outboundUrl = "https://api.orange.com/smsmessaging/v1/outbound/{senderAddress}/requests";
        }
        if (defaultCountryCode == null || defaultCountryCode.isBlank()) {
            defaultCountryCode = "225";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 30;
        }
    }
}
