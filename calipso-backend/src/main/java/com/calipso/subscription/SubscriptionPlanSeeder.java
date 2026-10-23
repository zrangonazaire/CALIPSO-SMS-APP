package com.calipso.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanSeeder implements CommandLineRunner {

    private final SubscriptionPlanRepository planRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        migrateLegacySchemaConstraints();
        seed(SubscriptionPlanCode.ESSENTIEL, "Essentiel", 20, 30, 100);
        seed(SubscriptionPlanCode.STANDARD, "Standard", 16, 60, 500);
        seed(SubscriptionPlanCode.AVANCE, "Avance", 12, 120, 1000);
        seed(SubscriptionPlanCode.VOLUME, "Volume", 10, 180, 5000);
    }

    private void migrateLegacySchemaConstraints() {
        String[] statements = {
                "ALTER TABLE campaigns ALTER COLUMN profile_id DROP NOT NULL",
                "ALTER TABLE campaigns ALTER COLUMN template_id DROP NOT NULL",
                "ALTER TABLE campaigns ALTER COLUMN phone_variable_id DROP NOT NULL",
                "ALTER TABLE campaign_recipients ALTER COLUMN phone_number DROP NOT NULL",
                "ALTER TABLE sms_templates ALTER COLUMN profile_id DROP NOT NULL"
        };
        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Migration SQL ignorée ({}): {}", sql, e.getMessage());
            }
        }
    }

    private void seed(SubscriptionPlanCode code, String name, int pricePerSms, int durationDays, int minRechargeUnits) {
        planRepository.findByCode(code).orElseGet(() -> planRepository.save(SubscriptionPlan.builder()
                .code(code)
                .name(name)
                .pricePerSms(pricePerSms)
                .durationDays(durationDays)
                .minRechargeUnits(minRechargeUnits)
                .active(true)
                .build()));
    }
}
