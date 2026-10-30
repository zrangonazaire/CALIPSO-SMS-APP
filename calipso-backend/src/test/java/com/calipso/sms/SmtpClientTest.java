package com.calipso.sms;

import com.calipso.integration.CompanyIntegrationSettingsService.DecryptedSettings;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.net.ssl.SSLHandshakeException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpClientTest {
    @Test void emailSendingUsesTheSameSecureSmtpFactoryAndReportsAuthenticationErrors() throws Exception {
        var settings = settings(465, false);
        var integrations = mock(com.calipso.integration.CompanyIntegrationSettingsService.class);
        var client = mock(SmtpClient.class);
        var sender = mock(JavaMailSenderImpl.class);
        when(integrations.findDecrypted(8L)).thenReturn(settings);
        when(client.createSender(settings)).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        var gateway = new MultichannelGatewayService(mock(OrangeSmsClient.class), integrations, client);
        var success = gateway.dispatch(8L, com.calipso.campaign.CampaignChannel.EMAIL,
                "Bonjour", "Test", null, "recipient@example.test", null, null, null);
        assertTrue(success.sent());
        verify(client).createSender(settings);
        verify(sender).send(any(jakarta.mail.internet.MimeMessage.class));
        doThrow(new org.springframework.mail.MailAuthenticationException("private-secret")).when(sender).send(any(jakarta.mail.internet.MimeMessage.class));
        var failed = gateway.dispatch(8L, com.calipso.campaign.CampaignChannel.EMAIL,
                "Bonjour", "Test", null, "recipient@example.test", null, null, null);
        assertFalse(failed.sent());
        assertTrue(failed.errorMessage().contains("Authentification SMTP refusée"));
        assertFalse(failed.errorMessage().contains("private-secret"));
    }

    private DecryptedSettings settings(int port, boolean tls) {
        return new DecryptedSettings(null, null, null, null, null, null,
                "mail.example.test", port, "sender@example.test", "smtp-user", "smtp-secret", tls, null);
    }

    @Test void port465UsesImplicitTlsAndDisablesStartTlsEvenIfTheOldSettingWasTrue() {
        var sender = new SmtpClient().createSender(settings(465, true));
        var properties = sender.getJavaMailProperties();
        assertEquals(465, sender.getPort());
        assertEquals(true, properties.get("mail.smtp.ssl.enable"));
        assertEquals(true, properties.get("mail.smtp.ssl.checkserveridentity"));
        assertEquals(false, properties.get("mail.smtp.starttls.enable"));
        assertEquals(false, properties.get("mail.smtp.starttls.required"));
        assertEquals(true, properties.get("mail.smtp.auth"));
    }

    @Test void port587UsesRequiredStartTlsWhenConfigured() {
        var properties = new SmtpClient().createSender(settings(587, true)).getJavaMailProperties();
        assertEquals(false, properties.get("mail.smtp.ssl.enable"));
        assertEquals(true, properties.get("mail.smtp.starttls.enable"));
        assertEquals(true, properties.get("mail.smtp.starttls.required"));
    }

    @Test void connectionTestOnlyConnectsAndAuthenticatesWithoutConstructingOrSendingMail() throws Exception {
        var client = spy(new SmtpClient());
        var settings = settings(465, false);
        var sender = mock(JavaMailSenderImpl.class);
        doReturn(sender).when(client).createSender(settings);
        var result = client.checkConnection(settings);
        assertTrue(result.connected());
        assertEquals("SSL/TLS", result.security());
        verify(sender).testConnection();
        verifyNoMoreInteractions(sender);
    }

    @Test void authenticationErrorsAreActionableAndNeverExposeCredentials() throws Exception {
        var client = spy(new SmtpClient());
        var settings = settings(465, false);
        var sender = mock(JavaMailSenderImpl.class);
        doReturn(sender).when(client).createSender(settings);
        doThrow(new AuthenticationFailedException("535 smtp-user smtp-secret")).when(sender).testConnection();
        var result = client.checkConnection(settings);
        assertFalse(result.connected());
        assertTrue(result.message().contains("Authentification SMTP refusée"));
        assertFalse(result.message().contains("smtp-secret"));
        assertFalse(result.message().contains("smtp-user"));
    }

    @Test void nestedTlsAndNetworkFailuresAreDistinguished() {
        assertTrue(SmtpClient.errorMessage(new MessagingException("Failed", new SSLHandshakeException("Certificate")))
                .contains("Connexion sécurisée SMTP impossible"));
        assertTrue(SmtpClient.errorMessage(new java.net.SocketTimeoutException()).contains("Délai"));
        assertTrue(SmtpClient.errorMessage(new java.net.UnknownHostException()).contains("introuvable"));
        assertTrue(SmtpClient.errorMessage(new java.net.ConnectException()).contains("refusée ou inaccessible"));
    }
}
