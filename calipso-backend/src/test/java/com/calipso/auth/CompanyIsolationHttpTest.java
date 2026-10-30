package com.calipso.auth;

import com.calipso.campaign.*;
import com.calipso.companyuser.*;
import com.calipso.compagny.*;
import com.calipso.config.ApiExceptionHandler;
import com.calipso.config.DataType;
import com.calipso.excel.*;
import com.calipso.excelvariable.*;
import com.calipso.importprofile.*;
import com.calipso.integration.*;
import com.calipso.recipient.*;
import com.calipso.sms.*;
import com.calipso.smstemplate.*;
import com.calipso.subscription.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyIsolationHttpTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final ExcelImportProfileRepository profiles = mock(ExcelImportProfileRepository.class);
    private final ExcelVariableRepository variables = mock(ExcelVariableRepository.class);
    private final SmsTemplateRepository templates = mock(SmsTemplateRepository.class);
    private final CampaignRecipientRepository recipients = mock(CampaignRecipientRepository.class);
    private final CompanyUserRepository users = mock(CompanyUserRepository.class);
    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final SmsSendHistoryRepository history = mock(SmsSendHistoryRepository.class);
    private final MultichannelGatewayService gateway = mock(MultichannelGatewayService.class);
    private final ExcelImportService excel = mock(ExcelImportService.class);
    private final HeaderModelService models = mock(HeaderModelService.class);
    private final AuthService auth = mock(AuthService.class);
    private final CompanyIntegrationSettingsService integrationSettings = mock(CompanyIntegrationSettingsService.class);
    private final AccessSettings settings = new AccessSettings();
    private CompanyAccess access;
    private MockMvc mvc;
    private Company magiser;
    private Company other;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        magiser = Company.builder().id(1L).name("MAGISER").active(true).build();
        other = Company.builder().id(2L).name("Autre entreprise").active(true).build();
        var tenant = CompanyUser.builder().id(10L).company(magiser).fullName("Zrango").role(CompanyUserRole.ADMIN).active(true).build();
        var platform = CompanyUser.builder().id(99L).company(magiser).role(CompanyUserRole.ADMIN).active(true).build();
        var operator = CompanyUser.builder().id(12L).company(magiser).role(CompanyUserRole.OPERATOR).active(true).build();
        settings.setPlatformAdminUserIds(Set.of(99L));
        access = new CompanyAccess(settings, profiles, campaigns);
        when(auth.authenticateToken("magiser")).thenReturn(tenant);
        when(auth.authenticateToken("platform")).thenReturn(platform);
        when(auth.authenticateToken("operator")).thenReturn(operator);
        when(companies.findAll()).thenReturn(List.of(magiser, other));
        when(companies.findById(1L)).thenReturn(Optional.of(magiser));
        when(companies.findById(2L)).thenReturn(Optional.of(other));
        when(companies.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var foreignProfile = ExcelImportProfile.builder().id(20L).company(other).name("Privé").active(true).build();
        when(profiles.findById(20L)).thenReturn(Optional.of(foreignProfile));
        when(profiles.findById(11L)).thenReturn(Optional.of(ExcelImportProfile.builder().id(11L).company(magiser).name("Clients").active(true).build()));
        when(campaigns.findById(200L)).thenReturn(Optional.of(Campaign.builder().id(200L).company(other).build()));
        when(campaigns.findById(100L)).thenReturn(Optional.of(Campaign.builder().id(100L).company(magiser).build()));
        when(templates.findById(40L)).thenReturn(Optional.of(SmsTemplate.builder().id(40L).company(other).name("Privé").content("Secret").build()));
        when(variables.findById(30L)).thenReturn(Optional.of(ExcelVariable.builder().id(30L).company(other).profile(foreignProfile).code("TEL").dataType(DataType.PHONE).build()));
        when(users.findById(50L)).thenReturn(Optional.of(CompanyUser.builder().id(50L).company(other).role(CompanyUserRole.ADMIN).active(true).build()));
        when(users.findById(99L)).thenReturn(Optional.of(platform));
        mvc = MockMvcBuilders.standaloneSetup(
                new CompanyController(access, companies, subscriptions),
                new CampaignController(access, campaigns, companies, profiles, templates, variables, recipients,
                        history, subscriptions, gateway, excel, mock(CampaignHeaderService.class)),
                new ExcelImportProfileController(access, profiles, companies, models),
                new ExcelVariableController(access, variables, profiles),
                new SmsTemplateController(access, templates, profiles, companies),
                new CampaignRecipientController(access, recipients),
                new ExcelImportController(excel, access),
                new CompanyUserController(users, companies, tokens, mock(PasswordEncoder.class), access),
                new ManualSmsController(access, companies, mock(SmsMessageService.class), history, subscriptions, gateway),
                new SmsSendHistoryController(access, history),
                new SmsApiController(access, mock(SmsApiDashboardService.class)),
                new SubscriptionController(access, subscriptions),
                new OrangeSmsDeliveryReceiptController(access, history),
                new CompanyIntegrationSettingsController(access, integrationSettings))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new BearerTokenAuthFilter(auth)).build();
    }

    @Test
    void companyIntegrationSecretsAreReservedForPlatformAdministrators() throws Exception {
        when(integrationSettings.get(2L)).thenReturn(new CompanyIntegrationSettingsResponse(
                2L, null, false, null, null, false, null, null, null, null, false, false, null));
        mvc.perform(get("/api/v1/companies/2/integrations").header("Authorization", "Bearer magiser"))
                .andExpect(status().isForbidden());

        var platformAdmin = CompanyUser.builder().id(99L).company(magiser)
                .role(CompanyUserRole.ADMIN).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(platformAdmin, null, List.of()));
        assertEquals(2L, new CompanyIntegrationSettingsController(access, integrationSettings).get(2L).companyId());
    }

    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void tenantAdministratorOnlyReceivesMagiserInTheCompanySelector() throws Exception {
        mvc.perform(get("/api/v1/companies").header("Authorization", "Bearer magiser"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MAGISER"));
        verify(companies, never()).findAll();
    }

    @Test
    void explicitlyAuthorizedPlatformAdministratorCanListAndReadOtherCompanies() throws Exception {
        mvc.perform(get("/api/v1/companies").header("Authorization", "Bearer platform"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/companies/2").header("Authorization", "Bearer platform"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Autre entreprise"));
    }

    @Test
    void accessFailsClosedWithoutAnAuthenticatedSession() throws Exception {
        mvc.perform(get("/api/v1/companies")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/companies/2", "/company-users/company/2", "/campaigns/company/2",
            "/recipients/campaign/200", "/import-profiles/company/2", "/import-profiles/20/sample-excel",
            "/excel-variables/profile/20", "/sms-templates/profile/20", "/sms-templates/company/2",
            "/sms-history/company/2", "/sms-api/company/2/dashboard", "/subscription/companies/2",
            "/subscription/companies/2/simulate", "/subscription/companies/2/transactions" })
    void rejectsForeignReadsEvenWithGuessedIds(String path) throws Exception {
        mvc.perform(get("/api/v1" + path).header("Authorization", "Bearer magiser"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(subscriptions, gateway, history, recipients, excel, models);
    }

    record Attack(String method, String path, String body) {}
    static Stream<Attack> foreignWrites() {
        String company = "{\"name\":\"Intrusion\"}";
        String model = "{\"companyId\":2,\"name\":\"Intrusion\",\"columns\":[{\"code\":\"NOM\",\"headerName\":\"Nom\",\"label\":\"Nom\",\"dataType\":\"TEXT\",\"required\":false,\"phone\":false}]}";
        return Stream.of(
                new Attack("POST", "/companies", company), new Attack("PUT", "/companies/2", company),
                new Attack("POST", "/companies/2/wallet/recharge", "{\"smsUnits\":10}"),
                new Attack("POST", "/campaigns", "{\"companyId\":2,\"name\":\"Intrusion\"}"),
                new Attack("POST", "/campaigns/wizard", "{\"companyId\":2,\"name\":\"Intrusion\",\"messageTemplate\":\"Bonjour\",\"sendImmediately\":false}"),
                new Attack("POST", "/campaigns/200/send", "{}"),
                new Attack("POST", "/manual-sms/send", "{\"companyId\":2,\"message\":\"Intrusion\",\"phoneNumbers\":[\"0701234567\"]}"),
                new Attack("POST", "/import-profiles", "{\"companyId\":2,\"name\":\"Intrusion\"}"),
                new Attack("POST", "/import-profiles/header-models", model),
                new Attack("PUT", "/import-profiles/20/header-model", model),
                new Attack("POST", "/import-profiles/20/duplicate", "{}"),
                new Attack("DELETE", "/import-profiles/20", ""),
                new Attack("POST", "/excel-variables", "{\"profileId\":20,\"code\":\"NOM\",\"label\":\"Nom\",\"dataType\":\"TEXT\"}"),
                new Attack("DELETE", "/excel-variables/30", ""),
                new Attack("POST", "/sms-templates", "{\"companyId\":2,\"name\":\"Intrusion\",\"content\":\"Secret\"}"),
                new Attack("POST", "/sms-templates", "{\"companyId\":1,\"profileId\":20,\"name\":\"Intrusion\",\"content\":\"Secret\"}"),
                new Attack("POST", "/campaigns", "{\"companyId\":1,\"profileId\":20,\"name\":\"Intrusion\"}"),
                new Attack("POST", "/campaigns", "{\"companyId\":1,\"templateId\":40,\"name\":\"Intrusion\"}"),
                new Attack("POST", "/campaigns", "{\"companyId\":1,\"phoneVariableId\":30,\"name\":\"Intrusion\"}"),
                new Attack("POST", "/subscription/companies/2/subscribe", "{\"planCode\":\"ESSENTIEL\"}"),
                new Attack("POST", "/subscription/companies/2/wallet/recharge", "{\"smsUnits\":10}"),
                new Attack("POST", "/company-users", "{\"companyId\":2,\"fullName\":\"Intrusion\",\"username\":\"intrusion\",\"email\":\"test@example.ci\",\"password\":\"password123\",\"role\":\"ADMIN\"}"),
                new Attack("PATCH", "/company-users/50/role?role=ADMIN", ""),
                new Attack("PATCH", "/company-users/50/active?active=false", ""),
                new Attack("POST", "/company-users/50/reset-password", "{\"newPassword\":\"intrusion\"}"),
                new Attack("DELETE", "/company-users/50", ""),
                new Attack("POST", "/company-users/99/reset-password", "{\"newPassword\":\"intrusion\"}"),
                new Attack("PATCH", "/company-users/99/role?role=OPERATOR", ""),
                new Attack("DELETE", "/company-users/99", ""),
                new Attack("POST", "/orange/sms-delivery-receipts", "{}"),
                new Attack("POST", "/subscription/payments/webhook", "{\"reference\":\"foreign-ref\",\"status\":\"SUCCESS\"}")
        );
    }

    @ParameterizedTest
    @MethodSource("foreignWrites")
    void rejectsForeignWritesAndPrivilegeEscalationBeforeSideEffects(Attack attack) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(attack.method()), "/api/v1" + attack.path())
                        .header("Authorization", "Bearer magiser").contentType(MediaType.APPLICATION_JSON).content(attack.body()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(subscriptions, gateway, history, recipients, excel, models);
        verify(companies, never()).save(any()); verify(campaigns, never()).save(any());
        verify(users, never()).save(any()); verify(tokens, never()).deleteByUserId(any());
    }

    @Test
    void rejectsImportIntoAnotherCompanyBeforeReadingTheFile() throws Exception {
        mvc.perform(multipart("/api/v1/excel/campaign/200/import")
                        .file(new MockMultipartFile("file", "clients.csv", "text/csv", "TEL\n0701234567".getBytes()))
                        .header("Authorization", "Bearer magiser"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(excel);
    }

    @Test
    void allowsOwnReadsAndExplicitPlatformAccess() throws Exception {
        mvc.perform(get("/api/v1/campaigns/company/1").header("Authorization", "Bearer magiser")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/campaigns/company/2").header("Authorization", "Bearer platform")).andExpect(status().isOk());
        verify(campaigns).findByCompanyIdOrderByCreatedAtDesc(1L);
        verify(campaigns).findByCompanyIdOrderByCreatedAtDesc(2L);
    }

    @Test
    void neverTreatsAnOrdinaryAdministratorAsAPlatformAdministrator() {
        assertFalse(access.isPlatformAdmin(CompanyUser.builder().id(10L).role(CompanyUserRole.ADMIN).active(true).build()));
        assertFalse(access.isPlatformAdmin(CompanyUser.builder().id(99L).role(CompanyUserRole.OPERATOR).active(true).build()));
        assertTrue(access.isPlatformAdmin(CompanyUser.builder().id(99L).role(CompanyUserRole.ADMIN).active(true).build()));
    }

    @Test
    void acceptsOnlyAnExplicitlyConfiguredPaymentWebhookSecret() throws Exception {
        settings.setPaymentWebhookSecret("trusted-provider-secret");
        mvc.perform(post("/api/v1/subscription/payments/webhook").header("X-Calipso-Webhook-Secret", "trusted-provider-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"paid-ref\"}")).andExpect(status().isOk());
        verify(subscriptions).handlePaymentWebhook(any());
    }
}
