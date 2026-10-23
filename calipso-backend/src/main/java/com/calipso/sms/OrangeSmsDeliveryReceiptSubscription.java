package com.calipso.sms;

public record OrangeSmsDeliveryReceiptSubscription(
        String resourceUrl,
        String subscriptionId,
        String providerResponse
) {
}
