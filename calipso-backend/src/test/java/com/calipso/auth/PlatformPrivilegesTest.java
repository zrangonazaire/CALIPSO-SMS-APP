package com.calipso.auth;

import com.calipso.campaign.CampaignChannel;
import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.*;
import com.calipso.compagny.*;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.sms.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformPrivilegesTest {
    private final CompanyUserRepository users = mock(CompanyUserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final AccessSettings settings = new AccessSettings();
    private final CompanyAccess access = new CompanyAccess(settings, mock(ExcelImportProfileRepository.class), mock(CampaignRepository.class));
    private final AuthService auth = new AuthService(users, companies, tokens, mock(PasswordEncoder.class), mock(MultichannelGatewayService.class), access);

    @Test
    void registeringOrSettingUpAnAccountNeverGrantsPlatformAccess() {
        when(companies.save(any())).thenAnswer(invocation -> { var c = invocation.<Company>getArgument(0); c.setId(1L); return c; });
        when(users.save(any())).thenAnswer(invocation -> { var u = invocation.<CompanyUser>getArgument(0); u.setId(10L); return u; });
        when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new SetupAdminRequest("MAGISER", "Zrango", "zrango", "zrango@example.ci", "password123");
        var registration = auth.register(request);
        assertEquals(CompanyUserRole.ADMIN, registration.user().role());
        assertFalse(registration.user().platformAdmin());
        assertFalse(auth.setupAdmin(request).user().platformAdmin());
    }

    @Test
    void passwordRecoveryNeverReturnsTheCodeToTheCaller() {
        var user = CompanyUser.builder().id(99L).company(Company.builder().id(1L).build()).active(true)
                .role(CompanyUserRole.ADMIN).username("root").fullName("Root").email("root@example.ci").build();
        when(users.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase("root", "root")).thenReturn(Optional.of(user));
        var response = auth.requestPasswordReset(new ForgotPasswordRequest("root", CampaignChannel.EMAIL));
        assertNotNull(user.getResetCode()); assertNull(response.devCodeHint());
        assertFalse(response.toString().contains(user.getResetCode()));
    }

    @Test
    void emptyPlatformConfigurationIsDenyByDefault() {
        var source = new MapConfigurationPropertySource(Map.of("calipso.security.platform-admin-user-ids", ""));
        var bound = new Binder(source).bind("calipso.security", AccessSettings.class).get();
        assertTrue(bound.getPlatformAdminUserIds().isEmpty());
    }

    @Test
    void tenantDashboardDoesNotExposeOrSynchronizeGlobalOrangeContracts() {
        var company = Company.builder().id(1L).name("MAGISER").smsBalance(50).build();
        var user = CompanyUser.builder().id(10L).company(company).role(CompanyUserRole.ADMIN).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        try {
            var orange = mock(OrangeSmsClient.class); var history = mock(SmsSendHistoryRepository.class);
            when(companies.findById(1L)).thenReturn(Optional.of(company));
            var response = new SmsApiDashboardService(companies, history, orange, access).getDashboard(1L);
            assertEquals(50, company.getSmsBalance()); assertNotNull(response);
            verifyNoInteractions(orange); verify(companies, never()).save(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
