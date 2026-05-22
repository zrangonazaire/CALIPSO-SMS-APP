package com.calipso.sms;

import java.time.LocalDateTime;
import java.util.List;

public record SmsApiDashboardResponse(
        Long companyId,
        String companyName,
        String senderPhone,
        int remainingSmsBalance,
        int totalMessages,
        int acceptedMessages,
        int deliveredMessages,
        int failedMessages,
        int pendingMessages,
        int consumedSegments,
        int failedSegments,
        double successRate,
        LocalDateTime lastActivityAt,
        List<OrangeContractResponse> contracts,
        List<SmsSendHistoryResponse> recentMessages
) {
    public record OrangeContractResponse(
            String id,
            String type,
            String country,
            String offerName,
            Integer availableUnits,
            Integer requestedUnits,
            String status,
            String expirationDate,
            String creationDate,
            String lastUpdateDate
    ) {
        public static OrangeContractResponse from(OrangeSmsContract contract) {
            return new OrangeContractResponse(
                    contract.id(),
                    contract.type(),
                    contract.country(),
                    contract.offerName(),
                    contract.availableUnits(),
                    contract.requestedUnits(),
                    contract.status(),
                    contract.expirationDate(),
                    contract.creationDate(),
                    contract.lastUpdateDate()
            );
        }
    }
}
