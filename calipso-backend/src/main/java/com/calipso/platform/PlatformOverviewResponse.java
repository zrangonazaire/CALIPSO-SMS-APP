package com.calipso.platform;

import java.time.LocalDateTime;
import java.util.List;

public record PlatformOverviewResponse(
        int companyCount,
        long activeCompanies,
        long suspendedCompanies,
        long remainingSmsCredits,
        long messageCount,
        long sentMessages,
        long deliveredMessages,
        long failedMessages,
        long pendingMessages,
        long activeSubscriptions,
        List<CompanySummary> companies
) {
    public record CompanySummary(
            Long id,
            String name,
            String email,
            String phone,
            boolean active,
            int smsBalance,
            long totalMessages,
            long acceptedMessages,
            long deliveredMessages,
            long failedMessages,
            long pendingMessages,
            long consumedSegments,
            long activeUsers,
            long campaigns,
            String subscriptionPlan,
            String subscriptionStatus,
            LocalDateTime subscriptionExpiresAt
    ) { }
}
