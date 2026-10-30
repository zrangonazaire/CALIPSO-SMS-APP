package com.calipso.subscription;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private SubscriptionPlanCode code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer pricePerSms;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Integer minRechargeUnits;

    private String serviceTier;
    private Integer durationMonths;
    private Integer packagePrice;
    private Integer includedWhatsapp;
    private Integer includedEmail;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    @PreUpdate
    public void normalize() {
        if (active == null) {
            active = true;
        }
        if (minRechargeUnits == null) {
            minRechargeUnits = 1;
        }
    }
}
