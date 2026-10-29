package com.calipso.statistics;

import com.calipso.auth.CompanyAccess;
import com.calipso.compagny.*;
import com.calipso.recipient.RecipientStatus;
import com.calipso.sms.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PeriodStatisticsServiceTest {
    @Test
    void recomputesCountersFromFilteredMessagesWithoutChangingTheCurrentBalance() {
        var companies = mock(CompanyRepository.class); var history = mock(SmsSendHistoryRepository.class);
        var gateway = mock(OrangeSmsClient.class); var access = mock(CompanyAccess.class);
        var company = Company.builder().id(1L).smsBalance(123).build();
        when(companies.findById(1L)).thenReturn(java.util.Optional.of(company));
        var sent = SmsSendHistory.builder().company(company).status(RecipientStatus.SENT).segmentCount(2)
                .sentAt(LocalDateTime.of(2026, 10, 28, 23, 59)).createdAt(LocalDateTime.of(2026, 10, 27, 12, 0)).build();
        var failed = SmsSendHistory.builder().company(company).status(RecipientStatus.FAILED).segmentCount(1)
                .createdAt(LocalDateTime.of(2026, 10, 28, 12, 0)).build();
        when(history.findForPeriod(eq(1L), any())).thenReturn(List.of(sent, failed));
        var result = new SmsApiDashboardService(companies, history, gateway, access)
                .getDashboard(1L, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28));
        assertEquals(2, result.totalMessages()); assertEquals(1, result.acceptedMessages());
        assertEquals(1, result.failedMessages()); assertEquals(2, result.consumedSegments());
        assertEquals(50.0, result.successRate()); assertEquals(123, result.remainingSmsBalance());
        assertEquals(sent.getSentAt(), result.lastActivityAt());
        verifyNoInteractions(gateway); verify(companies, never()).save(any());
    }
}
