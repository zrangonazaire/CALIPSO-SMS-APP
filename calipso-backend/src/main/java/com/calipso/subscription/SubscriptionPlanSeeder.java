package com.calipso.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionPlanSeeder implements CommandLineRunner {

    private final SubscriptionPlanRepository planRepository;

    @Override
    public void run(String... args) {
        seed(SubscriptionPlanCode.ESSENTIEL, "Essentiel", 20, 30, 100);
        seed(SubscriptionPlanCode.STANDARD, "Standard", 16, 60, 500);
        seed(SubscriptionPlanCode.AVANCE, "Avance", 12, 120, 1000);
        seed(SubscriptionPlanCode.VOLUME, "Volume", 10, 180, 5000);
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
