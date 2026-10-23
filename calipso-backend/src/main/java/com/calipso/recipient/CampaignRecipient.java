package com.calipso.recipient;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import com.calipso.campaign.Campaign;
import com.calipso.campaign.CampaignChannel;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaign_recipients")
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Campaign campaign;

    private String phoneNumber;

    private String email;

    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CampaignChannel channel = CampaignChannel.SMS;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(columnDefinition = "TEXT")
    private String generatedMessage;

    @Builder.Default
    private Integer segmentCount = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecipientStatus status = RecipientStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (segmentCount == null) {
            segmentCount = 1;
        }

        if (status == null) {
            status = RecipientStatus.PENDING;
        }

        if (channel == null) {
            channel = campaign != null ? campaign.resolvedChannel() : CampaignChannel.SMS;
        }
    }
}
