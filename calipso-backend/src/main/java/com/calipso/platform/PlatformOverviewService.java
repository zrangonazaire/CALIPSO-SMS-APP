package com.calipso.platform;

import com.calipso.auth.CompanyAccess;
import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.sms.SmsSendHistoryRepository;
import com.calipso.subscription.CompanySubscriptionRepository;
import com.calipso.subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformOverviewService {
    private final CompanyAccess access;
    private final CompanyRepository companies;
    private final SmsSendHistoryRepository history;
    private final CompanySubscriptionRepository subscriptions;
    private final CompanyUserRepository users;
    private final CampaignRepository campaigns;

    @Transactional(readOnly = true)
    public PlatformOverviewResponse getOverview() {
        access.requirePlatformAdmin();
        var allCompanies = companies.findAll();
        Map<Long, SmsSendHistoryRepository.CompanyMessageTotals> totalsByCompany = history.summarizeByCompany().stream()
                .collect(Collectors.toMap(SmsSendHistoryRepository.CompanyMessageTotals::getCompanyId, Function.identity()));

        var summaries = allCompanies.stream().map(company -> summarize(company, totalsByCompany.get(company.getId()))).toList();
        return new PlatformOverviewResponse(
                summaries.size(),
                summaries.stream().filter(PlatformOverviewResponse.CompanySummary::active).count(),
                summaries.stream().filter(summary -> !summary.active()).count(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::smsBalance).sum(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::totalMessages).sum(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::acceptedMessages).sum(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::deliveredMessages).sum(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::failedMessages).sum(),
                summaries.stream().mapToLong(PlatformOverviewResponse.CompanySummary::pendingMessages).sum(),
                summaries.stream().filter(summary -> "ACTIVE".equals(summary.subscriptionStatus())).count(),
                summaries
        );
    }

    private PlatformOverviewResponse.CompanySummary summarize(
            Company company,
            SmsSendHistoryRepository.CompanyMessageTotals totals
    ) {
        var subscription = subscriptions.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                company.getId(), SubscriptionStatus.ACTIVE).orElse(null);
        String subscriptionStatus = subscription == null ? "NONE"
                : subscription.getExpiresAt().isBefore(LocalDateTime.now()) ? "EXPIRED" : "ACTIVE";
        int balance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        return new PlatformOverviewResponse.CompanySummary(
                company.getId(),
                company.getName(),
                company.getEmail(),
                company.getPhone(),
                Boolean.TRUE.equals(company.getActive()),
                balance,
                totals == null ? 0 : safe(totals.getTotalMessages()),
                totals == null ? 0 : safe(totals.getAcceptedMessages()),
                totals == null ? 0 : safe(totals.getDeliveredMessages()),
                totals == null ? 0 : safe(totals.getFailedMessages()),
                totals == null ? 0 : safe(totals.getPendingMessages()),
                totals == null ? 0 : safe(totals.getConsumedSegments()),
                users.countByCompanyIdAndActiveTrue(company.getId()),
                campaigns.countByCompanyId(company.getId()),
                subscription == null ? null : subscription.getPlan().getName(),
                subscriptionStatus,
                subscription == null ? null : subscription.getExpiresAt()
        );
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }
}
