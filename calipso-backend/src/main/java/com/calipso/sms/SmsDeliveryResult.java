package com.calipso.sms;

public record SmsDeliveryResult(
        boolean sent,
        int statusCode,
        String errorMessage,
        String providerResponse,
        String providerResourceUrl,
        String providerResourceId,
        String deliveryCallbackData
) {
    public static SmsDeliveryResult sent(
            int statusCode,
            String providerResponse,
            String providerResourceUrl,
            String providerResourceId,
            String deliveryCallbackData
    ) {
        return new SmsDeliveryResult(
                true,
                statusCode,
                null,
                providerResponse,
                providerResourceUrl,
                providerResourceId,
                deliveryCallbackData
        );
    }

    public static SmsDeliveryResult failed(int statusCode, String errorMessage) {
        return new SmsDeliveryResult(false, statusCode, errorMessage, null, null, null, null);
    }
}
