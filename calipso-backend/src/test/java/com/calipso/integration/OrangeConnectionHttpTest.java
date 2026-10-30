package com.calipso.integration;

import com.calipso.auth.*;
import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.*;
import com.calipso.compagny.Company;
import com.calipso.config.ApiExceptionHandler;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.sms.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrangeConnectionHttpTest {
    private final CompanyIntegrationSettingsService settings = mock(CompanyIntegrationSettingsService.class);
    private final OrangeSmsClient client = mock(OrangeSmsClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var policy = new AccessSettings(); policy.setPlatformAdminUserIds(Set.of(99L));
        var access = new CompanyAccess(policy, mock(ExcelImportProfileRepository.class), mock(CampaignRepository.class));
        var properties = mock(OrangeSmsProperties.class);
        when(properties.tokenUrl()).thenReturn("https://api.orange.com/oauth/v3/token");
        when(properties.outboundUrl()).thenReturn("https://api.orange.com/smsmessaging/v1/outbound/{senderAddress}/requests");
        when(properties.contractsUrl()).thenReturn("https://api.orange.com/sms/admin/v1/contracts?country=CIV");
        mvc = MockMvcBuilders.standaloneSetup(new OrangeConnectionController(access, settings, client, properties))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    private void login(long id) {
        var user = CompanyUser.builder().id(id).company(Company.builder().id(8L).build()).role(CompanyUserRole.ADMIN).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void tenantAdministratorsCannotReadOrTestCompanySecrets() throws Exception {
        login(10L);
        mvc.perform(get("/api/v1/companies/8/integrations/orange/configuration")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/companies/8/integrations/orange/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(settings, client);
    }
    @Test void testsStoredCredentialsWithoutWritingSettings() throws Exception {
        login(99L);
        when(settings.findDecrypted(8L)).thenReturn(new CompanyIntegrationSettingsService.DecryptedSettings(
                "+2250000", "saved-id", "saved-secret", null, null, null, null, null, null, null, null, false, null));
        when(client.checkCompanyConnection("saved-id", "saved-secret")).thenReturn(new OrangeSmsClient.ConnectionStatus(
                true, "Bearer", Instant.now().plusSeconds(3600), true, 12, List.of(), "Connexion vérifiée"));
        mvc.perform(post("/api/v1/companies/8/integrations/orange/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.availableUnits").value(12)).andExpect(jsonPath("$.access_token").doesNotExist());
        verify(settings, never()).update(anyLong(), any());
    }
    @Test void partialDraftCredentialsAreRejectedAndTechnicalUrlsUseTheEncodedSender() throws Exception {
        login(99L);
        mvc.perform(post("/api/v1/companies/8/integrations/orange/test").contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"draft\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(settings, client);
        mvc.perform(get("/api/v1/companies/8/integrations/orange/configuration")).andExpect(status().isOk())
                .andExpect(jsonPath("$.messagingUrl").value("https://api.orange.com/smsmessaging/v1/outbound/tel%3A%2B2250000/requests"));
    }
}
