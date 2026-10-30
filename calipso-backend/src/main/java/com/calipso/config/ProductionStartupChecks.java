package com.calipso.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Base64;

/** Refuse a production startup with missing or development secrets. Never log their values. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionStartupChecks {
    private final Environment environment;

    @PostConstruct
    public void validate() {
        requireSecret("spring.datasource.password", 24);
        requireSecret("calipso.security.bootstrap.password", 20);
        requireSecret("calipso.security.payment-webhook-secret", 32);
        String encoded = environment.getProperty("calipso.integrations.encryption-key", "").trim();
        try {
            if (Base64.getDecoder().decode(encoded).length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production : la clé de chiffrement doit contenir 32 octets encodés en Base64.");
        }
    }

    private void requireSecret(String property, int minimumLength) {
        String value = environment.getProperty(property, "");
        if (value.length() < minimumLength || value.equals("GESTIMOWEB@2026#")
                || value.equals("calipso_password")) {
            throw new IllegalStateException("Production : configurez un secret distinct d’au moins "
                    + minimumLength + " caractères pour " + property + ".");
        }
    }
}
