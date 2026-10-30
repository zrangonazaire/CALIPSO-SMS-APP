package com.calipso.sms;

import com.calipso.integration.CompanyIntegrationSettingsService.DecryptedSettings;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;

@Service
public class SmtpClient {
    public record ConnectionStatus(boolean connected, String security, String message) {}

    public JavaMailSenderImpl createSender(DecryptedSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        int port = settings.smtpPort() == null ? 587 : settings.smtpPort();
        boolean ssl = port == 465;
        boolean startTls = !ssl && settings.smtpStartTls();
        sender.setHost(settings.smtpHost());
        sender.setPort(port);
        sender.setUsername(settings.smtpUsername());
        sender.setPassword(settings.smtpPassword());
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", settings.smtpUsername() != null && !settings.smtpUsername().isBlank());
        properties.put("mail.smtp.ssl.enable", ssl);
        properties.put("mail.smtp.ssl.checkserveridentity", true);
        properties.put("mail.smtp.starttls.enable", startTls);
        properties.put("mail.smtp.starttls.required", startTls);
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "20000");
        properties.put("mail.smtp.writetimeout", "20000");
        return sender;
    }

    public ConnectionStatus checkConnection(DecryptedSettings settings) {
        String security = Integer.valueOf(465).equals(settings.smtpPort()) ? "SSL/TLS" : settings.smtpStartTls() ? "STARTTLS" : "SMTP";
        try {
            // Connect, authenticate and disconnect only. No MAIL FROM, RCPT TO or DATA.
            createSender(settings).testConnection();
            return new ConnectionStatus(true, security, settings.smtpUsername() == null || settings.smtpUsername().isBlank()
                    ? "Connexion SMTP vérifiée sans authentification. Aucun e-mail envoyé."
                    : "Connexion et authentification SMTP vérifiées. Aucun e-mail envoyé.");
        } catch (Exception exception) {
            return new ConnectionStatus(false, security, errorMessage(exception));
        }
    }

    public static String errorMessage(Throwable exception) {
        var causes = new ArrayList<Throwable>();
        var queue = new ArrayDeque<Throwable>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        queue.add(exception);
        while (!queue.isEmpty() && seen.size() < 32) {
            var current = queue.remove();
            if (!seen.add(current)) continue;
            causes.add(current);
            if (current.getCause() != null) queue.add(current.getCause());
            if (current instanceof MessagingException mail && mail.getNextException() != null) queue.add(mail.getNextException());
            if (current instanceof MailSendException mail) Collections.addAll(queue, mail.getMessageExceptions());
        }
        if (causes.stream().anyMatch(e -> e instanceof AuthenticationFailedException || e instanceof MailAuthenticationException))
            return "Authentification SMTP refusée. Vérifiez le nom d’utilisateur et le mot de passe SMTP ou le mot de passe d’application.";
        if (causes.stream().anyMatch(e -> e instanceof SSLException))
            return "Connexion sécurisée SMTP impossible. Vérifiez le certificat du serveur et le mode SSL/TLS (465) ou STARTTLS (587).";
        if (causes.stream().anyMatch(e -> e instanceof UnknownHostException))
            return "Serveur SMTP introuvable. Vérifiez son nom et la résolution DNS.";
        if (causes.stream().anyMatch(e -> e instanceof SocketTimeoutException))
            return "Délai de connexion ou de réponse SMTP dépassé. Vérifiez le port, le chiffrement et l’accès réseau.";
        if (causes.stream().anyMatch(e -> e instanceof ConnectException))
            return "Connexion au serveur SMTP refusée ou inaccessible. Vérifiez le serveur, le port et le pare-feu.";
        return "Le serveur SMTP a refusé l’opération. Vérifiez les paramètres, l’adresse d’expéditeur autorisée et les restrictions du compte.";
    }
}
