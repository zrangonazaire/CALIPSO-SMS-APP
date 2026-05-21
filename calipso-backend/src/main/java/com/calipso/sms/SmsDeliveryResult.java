package com.calipso.sms;

public record SmsDeliveryResult(
        boolean sent,
        int statusCode,
        String errorMessage
) {
    public static SmsDeliveryResult sent(int statusCode) {
        return new SmsDeliveryResult(true, statusCode, null);
    }

    public static SmsDeliveryResult failed(int statusCode, String errorMessage) {
        return new SmsDeliveryResult(false, statusCode, errorMessage);
    }
}
