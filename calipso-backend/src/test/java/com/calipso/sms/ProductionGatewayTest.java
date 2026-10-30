package com.calipso.sms;

import com.calipso.campaign.CampaignChannel;
import com.calipso.integration.CompanyIntegrationSettingsService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionGatewayTest {
    @ParameterizedTest
    @EnumSource(CampaignChannel.class)
    void neverUsesGlobalCredentialsOrSimulatesDeliveryWhenCompanyIsNotConfigured(CampaignChannel channel) {
        var orange = mock(OrangeSmsClient.class);
        var settings = mock(CompanyIntegrationSettingsService.class);
        var smtp = mock(SmtpClient.class);
        var gateway = new MultichannelGatewayService(orange, settings, smtp);
        ReflectionTestUtils.setField(gateway, "requireCompanySettings", true);
        ReflectionTestUtils.setField(gateway, "whatsappAccessToken", "global-token");
        ReflectionTestUtils.setField(gateway, "whatsappPhoneNumberId", "global-phone");
        var result = gateway.dispatch(8L, channel, "test", "test", null, "test@example.com", null, null, null);
        assertFalse(result.sent());
        assertEquals(503, result.statusCode());
        verifyNoInteractions(orange, smtp);
    }
}
