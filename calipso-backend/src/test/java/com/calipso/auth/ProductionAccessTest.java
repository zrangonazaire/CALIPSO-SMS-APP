package com.calipso.auth;

import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.compagny.CompanyRepository;
import com.calipso.importprofile.ExcelImportProfileRepository;
import com.calipso.sms.MultichannelGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionAccessTest {
    @Test void closedRegistrationRejectsBothPublicCreationRoutesBeforeWritingToDatabase() {
        var settings = new AccessSettings();
        settings.setPublicRegistrationEnabled(false);
        var users = mock(CompanyUserRepository.class);
        var companies = mock(CompanyRepository.class);
        var tokens = mock(AuthTokenRepository.class);
        var access = new CompanyAccess(settings, mock(ExcelImportProfileRepository.class), mock(CampaignRepository.class));
        var auth = new AuthService(users, companies, tokens, mock(PasswordEncoder.class), mock(MultichannelGatewayService.class), access);
        var request = new SetupAdminRequest("Unknown", "User", "user", "user@example.com", "password123");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> auth.register(request)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> auth.setupAdmin(request)).getStatusCode().value());
        verifyNoInteractions(users, companies, tokens);
    }
}
