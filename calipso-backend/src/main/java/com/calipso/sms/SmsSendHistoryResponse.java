package com.calipso.sms;

import java.time.LocalDateTime;

public record SmsSendHistoryResponse(
        Long id,
        Long companyId,
        String companyName,
        Long campaignId,
        String campaignName,
        SmsSendSource source,
        String phoneNumber,
        String message,
        Integer segmentCount,
        String status,
        String errorMessage,
        String providerResourceUrl,
        String providerResourceId,
        String deliveryStatus,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {
    public static SmsSendHistoryResponse from(SmsSendHistory history) {
        return new SmsSendHistoryResponse(
                history.getId(),
                history.getCompany().getId(),
                history.getCompany().getName(),
                history.getCampaign() == null ? null : history.getCampaign().getId(),
                history.getCampaign() == null ? null : history.getCampaign().getName(),
                history.getSource(),
                history.getPhoneNumber(),
                history.getMessage(),
                history.getSegmentCount(),
                history.getStatus().name(),
                history.getErrorMessage(),
                history.getProviderResourceUrl(),
                history.getProviderResourceId(),
                history.getDeliveryStatus(),
                history.getSentAt(),
                history.getDeliveredAt(),
                history.getCreatedAt()
        );
    }
}
