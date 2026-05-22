package com.calipso.sms;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.recipient.RecipientStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsApiDashboardService {

    private final CompanyRepository companyRepository;
    private final SmsSendHistoryRepository historyRepository;
    private final OrangeSmsClient orangeSmsClient;

    @Transactional
    public SmsApiDashboardResponse getDashboard(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));

        List<OrangeSmsContract> contracts = orangeSmsClient.fetchSmsContracts();
        int orangeBalance = contracts.stream()
                .filter(contract -> "ACTIVE".equalsIgnoreCase(contract.status()))
                .mapToInt(contract -> contract.availableUnits() == null ? 0 : contract.availableUnits())
                .sum();

        int currentBalance = company.getSmsBalance() == null ? 0 : company.getSmsBalance();
        if (orangeBalance != currentBalance) {
            log.info(
                    "[SMS API] Synchronisation solde: companyId={}, ancienSolde={}, soldeOrange={}",
                    company.getId(),
                    currentBalance,
                    orangeBalance
            );
            company.setSmsBalance(orangeBalance);
            companyRepository.save(company);
        }

        List<SmsSendHistory> history = historyRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        int totalMessages = history.size();
        int acceptedMessages = countByStatus(history, RecipientStatus.SENT) + countByStatus(history, RecipientStatus.DELIVERED);
        int deliveredMessages = countByStatus(history, RecipientStatus.DELIVERED);
        int failedMessages = countByStatus(history, RecipientStatus.FAILED) + countByStatus(history, RecipientStatus.INVALID);
        int pendingMessages = countByStatus(history, RecipientStatus.PENDING) + countByStatus(history, RecipientStatus.VALID);
        int consumedSegments = sumSegmentsByAcceptedStatus(history);
        int failedSegments = sumSegmentsByStatus(history, RecipientStatus.FAILED) + sumSegmentsByStatus(history, RecipientStatus.INVALID);
        double successRate = totalMessages == 0 ? 0 : (acceptedMessages * 100.0) / totalMessages;
        LocalDateTime lastActivityAt = history.isEmpty() ? null : history.getFirst().getCreatedAt();

        return new SmsApiDashboardResponse(
                company.getId(),
                company.getName(),
                company.getSenderPhone(),
                orangeBalance,
                totalMessages,
                acceptedMessages,
                deliveredMessages,
                failedMessages,
                pendingMessages,
                consumedSegments,
                failedSegments,
                Math.round(successRate * 10.0) / 10.0,
                lastActivityAt,
                contracts.stream().map(SmsApiDashboardResponse.OrangeContractResponse::from).toList(),
                history.stream().limit(8).map(SmsSendHistoryResponse::from).toList()
        );
    }

    private int countByStatus(List<SmsSendHistory> history, RecipientStatus status) {
        return (int) history.stream()
                .filter(item -> item.getStatus() == status)
                .count();
    }

    private int sumSegmentsByAcceptedStatus(List<SmsSendHistory> history) {
        return history.stream()
                .filter(item -> item.getStatus() == RecipientStatus.SENT || item.getStatus() == RecipientStatus.DELIVERED)
                .mapToInt(this::segmentCount)
                .sum();
    }

    private int sumSegmentsByStatus(List<SmsSendHistory> history, RecipientStatus status) {
        return history.stream()
                .filter(item -> item.getStatus() == status)
                .mapToInt(this::segmentCount)
                .sum();
    }

    private int segmentCount(SmsSendHistory history) {
        return history.getSegmentCount() == null ? 1 : history.getSegmentCount();
    }
}
