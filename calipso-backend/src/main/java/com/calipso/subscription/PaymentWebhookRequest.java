package com.calipso.subscription;

import jakarta.validation.constraints.NotBlank;

public record PaymentWebhookRequest(
        @NotBlank String reference,
        String status,
        String providerTransactionId,
        String operator,
        String payerPhone
) {
}
