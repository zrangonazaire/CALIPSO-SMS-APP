package com.calipso.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStartupChecksTest {
    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "db-" + "a".repeat(32))
                .withProperty("calipso.security.bootstrap.password", "admin-" + "b".repeat(32))
                .withProperty("calipso.security.payment-webhook-secret", "webhook-" + "c".repeat(32))
                .withProperty("calipso.integrations.encryption-key", Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test void refusesMissingSecretsWithoutDisclosingTheirValues() {
        var environment = validEnvironment().withProperty("spring.datasource.password", "private-short");
        var failure = assertThrows(IllegalStateException.class, () -> new ProductionStartupChecks(environment).validate());
        assertFalse(failure.getMessage().contains("private-short"));
    }

    @Test void refusesInvalidEncryptionKey() {
        var environment = validEnvironment().withProperty("calipso.integrations.encryption-key", "invalid");
        assertThrows(IllegalStateException.class, () -> new ProductionStartupChecks(environment).validate());
    }

    @Test void acceptsProductionSecrets() {
        assertDoesNotThrow(() -> new ProductionStartupChecks(validEnvironment()).validate());
    }
}
