package com.calipso.sms;

import com.calipso.campaign.Campaign;
import com.calipso.compagny.Company;
import com.calipso.recipient.RecipientStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sms_send_history")
public class SmsSendHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @ManyToOne
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsSendSource source;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private Integer segmentCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientStatus status = RecipientStatus.SENT;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String providerResponse;

    @Column(columnDefinition = "TEXT")
    private String providerResourceUrl;

    private String providerResourceId;

    private String deliveryStatus;

    @Column(columnDefinition = "TEXT")
    private String deliveryReceiptPayload;

    private LocalDateTime sentAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == RecipientStatus.SENT && sentAt == null) {
            sentAt = LocalDateTime.now();
        }

        if (segmentCount == null) {
            segmentCount = 1;
        }

        if (status == null) {
            status = RecipientStatus.SENT;
        }
    }
}
