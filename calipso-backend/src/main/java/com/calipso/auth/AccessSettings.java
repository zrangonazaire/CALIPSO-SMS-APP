package com.calipso.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "calipso.security")
@Getter
@Setter
public class AccessSettings {
    // No account receives platform access unless its persisted ID is explicitly configured.
    private Set<Long> platformAdminUserIds = new HashSet<>();
    private String paymentWebhookSecret = "";
}
