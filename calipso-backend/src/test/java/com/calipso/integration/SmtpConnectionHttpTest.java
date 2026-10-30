package com.calipso.integration;

import com.calipso.auth.*;
import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.*;
import com.calipso.compagny.Company;
import com.calipso.config.ApiExceptionHandler;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.sms.SmtpClient;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SmtpConnectionHttpTest {
    private final CompanyIntegrationSettingsService settings = mock(CompanyIntegrationSettingsService.class);
    private final SmtpClient smtp = mock(SmtpClient.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        var policy = new AccessSettings(); policy.setPlatformAdminUserIds(Set.of(99L));
        var access = new CompanyAccess(policy, mock(ExcelImportProfileRepository.class), mock(CampaignRepository.class));
        mvc = MockMvcBuilders.standaloneSetup(new SmtpConnectionController(access, settings, smtp))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    private void login(long id) {
        var user = CompanyUser.builder().id(id).company(Company.builder().id(8L).build()).role(CompanyUserRole.ADMIN).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void onlyExplicitlyAuthorizedPlatformAdministratorsCanTestSmtp() throws Exception {
        login(10L);
        mvc.perform(post("/api/v1/companies/8/integrations/smtp/test")).andExpect(status().isForbidden());
        verifyNoInteractions(settings, smtp);
    }
    @Test void testsOnlyTheSelectedCompanySavedConfigurationWithoutWritingOrReturningSecrets() throws Exception {
        login(99L);
        var saved = new CompanyIntegrationSettingsService.DecryptedSettings(null, null, null, null, null, null,
                "smtp.example.test", 465, "sender@example.test", "user", "secret", false, null);
        when(settings.findDecrypted(8L)).thenReturn(saved);
        when(smtp.checkConnection(saved)).thenReturn(new SmtpClient.ConnectionStatus(true, "SSL/TLS", "Connexion vérifiée"));
        mvc.perform(post("/api/v1/companies/8/integrations/smtp/test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.security").value("SSL/TLS"))
                .andExpect(jsonPath("$.smtpPassword").doesNotExist());
        verify(settings, never()).update(anyLong(), any());
        verify(smtp).checkConnection(saved);
    }
    @Test void missingCompanyConfigurationIsRejectedWithoutUsingGlobalSmtp() throws Exception {
        login(99L);
        mvc.perform(post("/api/v1/companies/8/integrations/smtp/test")).andExpect(status().isBadRequest());
        verifyNoInteractions(smtp);
    }
}
