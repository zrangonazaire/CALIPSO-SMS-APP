package com.calipso.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms.orange")
public record OrangeSmsProperties(
        String tokenUrl,
        String outboundUrl,
        String subscriptionUrl,
        String contractsUrl,
        String basicAuthorization,
        String clientId,
        String clientSecret,
        String defaultSenderAddress,
        String defaultCountryCode,
        String senderName,
        String deliveryReceiptUrl,
        String deliveryReceiptSubscriptionId,
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
        if (subscriptionUrl == null || subscriptionUrl.isBlank()) {
            subscriptionUrl = "https://api.orange.com/smsmessaging/v1/outbound/{senderAddress}/subscriptions";
        }
        if (contractsUrl == null || contractsUrl.isBlank()) {
            contractsUrl = "https://api.orange.com/sms/admin/v1/contracts?country=CIV";
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
