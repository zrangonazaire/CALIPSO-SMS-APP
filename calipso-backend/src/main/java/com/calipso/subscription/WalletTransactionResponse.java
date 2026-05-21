package com.calipso.subscription;

import com.calipso.billing.TransactionType;

import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        Long companyId,
        String companyName,
        Long subscriptionId,
        TransactionType type,
        Integer smsUnits,
        Integer unitPrice,
        Integer amount,
        String reason,
        LocalDateTime createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getCompany().getId(),
                transaction.getCompany().getName(),
                transaction.getSubscription() == null ? null : transaction.getSubscription().getId(),
                transaction.getType(),
                transaction.getSmsUnits(),
                transaction.getUnitPrice(),
                transaction.getAmount(),
                transaction.getReason(),
                transaction.getCreatedAt()
        );
    }
}
