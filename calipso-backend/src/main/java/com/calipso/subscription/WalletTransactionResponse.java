package com.calipso.subscription;

import com.calipso.billing.TransactionType;

import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        Long companyId,
        String companyName,
        Long subscriptionId,
        String planName,
        TransactionType type,
        Integer smsUnits,
        Integer unitPrice,
        Integer amount,
        Integer balanceAfter,
        String reference,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String payerPhone,
        String providerName,
        String checkoutUrl,
        String reason,
        LocalDateTime createdAt,
        com.calipso.campaign.CampaignChannel channel,
        Integer whatsappUnits,
        Integer emailUnits
) {
    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getCompany().getId(),
                transaction.getCompany().getName(),
                transaction.getSubscription() == null ? null : transaction.getSubscription().getId(),
                transaction.getSubscription() == null || transaction.getSubscription().getPlan() == null
                        ? transaction.getPurchasedPlan() == null ? null : transaction.getPurchasedPlan().getName()
                        : transaction.getSubscription().getPlan().getName(),
                transaction.getType(),
                transaction.getSmsUnits(),
                transaction.getUnitPrice(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReference(),
                transaction.getPaymentMethod() == null ? PaymentMethod.SYSTEM : transaction.getPaymentMethod(),
                transaction.getPaymentStatus() == null ? PaymentStatus.COMPLETED : transaction.getPaymentStatus(),
                transaction.getPayerPhone(),
                transaction.getProviderName(),
                transaction.getCheckoutUrl(),
                transaction.getReason(),
                transaction.getCreatedAt(), transaction.getChannel(), transaction.getWhatsappUnits(), transaction.getEmailUnits()
        );
    }
}
