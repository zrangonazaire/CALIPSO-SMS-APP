package com.calipso.subscription;

import java.util.List;

/** Prices and total channel allowances for one company and the entire selected term. */
public final class MultichannelCatalog {
    private MultichannelCatalog() {}

    public record Offer(SubscriptionPlanCode code, String tier, String name, int months,
                        int price, int sms, int whatsapp, int email) {}

    public static final List<Offer> OFFERS = List.of(
            new Offer(SubscriptionPlanCode.ESSENTIEL_3M, "ESSENTIEL", "Essentiel", 3, 75_000, 1_500, 750, 15_000),
            new Offer(SubscriptionPlanCode.ESSENTIEL_6M, "ESSENTIEL", "Essentiel", 6, 135_000, 3_000, 1_500, 30_000),
            new Offer(SubscriptionPlanCode.ESSENTIEL_12M, "ESSENTIEL", "Essentiel", 12, 240_000, 6_000, 3_000, 60_000),
            new Offer(SubscriptionPlanCode.PROFESSIONNEL_3M, "PROFESSIONNEL", "Professionnel", 3, 180_000, 6_000, 1_500, 45_000),
            new Offer(SubscriptionPlanCode.PROFESSIONNEL_6M, "PROFESSIONNEL", "Professionnel", 6, 330_000, 12_000, 3_000, 90_000),
            new Offer(SubscriptionPlanCode.PROFESSIONNEL_12M, "PROFESSIONNEL", "Professionnel", 12, 600_000, 24_000, 6_000, 180_000),
            new Offer(SubscriptionPlanCode.COMPLET_3M, "COMPLET", "Complet", 3, 360_000, 15_000, 3_000, 90_000),
            new Offer(SubscriptionPlanCode.COMPLET_6M, "COMPLET", "Complet", 6, 660_000, 30_000, 6_000, 180_000),
            new Offer(SubscriptionPlanCode.COMPLET_12M, "COMPLET", "Complet", 12, 1_200_000, 60_000, 12_000, 360_000)
    );
}
