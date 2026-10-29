package com.calipso.statistics;

import com.calipso.auth.*;
import com.calipso.campaign.*;
import com.calipso.companyuser.*;
import com.calipso.compagny.Company;
import com.calipso.config.ApiExceptionHandler;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.sms.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatisticsPeriodHttpTest {
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final SmsSendHistoryRepository history = mock(SmsSendHistoryRepository.class);
    private final SmsApiDashboardService statistics = mock(SmsApiDashboardService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var company = Company.builder().id(1L).name("MAGISER").build();
        var user = CompanyUser.builder().id(10L).company(company).role(CompanyUserRole.ADMIN).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        var access = new CompanyAccess(new AccessSettings(), mock(ExcelImportProfileRepository.class), campaigns);
        doCallRealMethod().when(campaigns).findForPeriod(anyLong(), any());
        doCallRealMethod().when(history).findForPeriod(anyLong(), any());
        var campaignController = new CampaignController(access, campaigns, null, null, null, null, null, null, null, null, null, null);
        mvc = MockMvcBuilders.standaloneSetup(campaignController, new SmsSendHistoryController(access, history), new SmsApiController(access, statistics))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void translatesAnInclusiveLastDayIntoAnExclusiveNextMidnight() {
        var period = StatisticsPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), period.startInclusive());
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 0), period.endExclusive());
        assertTrue(LocalDateTime.of(2026, 10, 28, 23, 59, 59, 999_999_999).isBefore(period.endExclusive()));
        var leapMonth = StatisticsPeriod.of(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29));
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), leapMonth.endExclusive());
        assertNull(StatisticsPeriod.of(null, null).startInclusive());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> StatisticsPeriod.of(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 1))).getStatusCode());
    }

    @Test
    void filtersCampaignsAndHistoryAtTheDatabaseBoundary() throws Exception {
        mvc.perform(get("/api/v1/campaigns/company/1").param("startDate", "2026-10-01").param("endDate", "2026-10-28"))
                .andExpect(status().isOk());
        verify(campaigns).findByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(1L,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 29, 0, 0));
        mvc.perform(get("/api/v1/sms-history/company/1").param("startDate", "2026-10-01").param("endDate", "2026-10-28"))
                .andExpect(status().isOk());
        verify(history).findWithinPeriod(1L, LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 29, 0, 0));
    }

    @Test
    void supportsOpenBoundsAndAllTimeRequests() throws Exception {
        mvc.perform(get("/api/v1/sms-history/company/1").param("startDate", "2026-10-01")).andExpect(status().isOk());
        verify(history).findSince(1L, LocalDateTime.of(2026, 10, 1, 0, 0));
        mvc.perform(get("/api/v1/sms-history/company/1").param("endDate", "2026-10-28")).andExpect(status().isOk());
        verify(history).findBefore(1L, LocalDateTime.of(2026, 10, 29, 0, 0));
        mvc.perform(get("/api/v1/campaigns/company/1")).andExpect(status().isOk());
        verify(campaigns).findByCompanyIdOrderByCreatedAtDesc(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/campaigns/company/1", "/sms-history/company/1" })
    void refusesReversedOrMalformedPeriodsBeforeQuerying(String path) throws Exception {
        mvc.perform(get("/api/v1" + path).param("startDate", "2026-10-29").param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1" + path).param("startDate", "bad-date"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(campaigns, history);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/campaigns/company/2", "/sms-history/company/2", "/sms-api/company/2/dashboard" })
    void periodParametersNeverBypassCompanyIsolation(String path) throws Exception {
        mvc.perform(get("/api/v1" + path).param("startDate", "2026-10-01").param("endDate", "2026-10-28"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(campaigns, history, statistics);
    }
}
