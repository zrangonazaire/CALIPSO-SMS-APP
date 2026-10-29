package com.calipso.subscription;

import com.calipso.billing.TransactionType;
import com.calipso.compagny.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @ManyToOne
    private CompanySubscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private Integer smsUnits;

    private Integer unitPrice;

    private Integer amount;

    private Integer balanceAfter;

    private String reference;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private String payerPhone;

    private String providerName;

    private String checkoutUrl;

    @Column(nullable = false)
    private String reason;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.COMPLETED;
        }
        if (paymentMethod == null) {
            paymentMethod = type == TransactionType.CREDIT ? PaymentMethod.WAVE_CI : PaymentMethod.SYSTEM;
        }
    }
}
