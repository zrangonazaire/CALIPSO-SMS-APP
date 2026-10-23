package com.calipso.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.calipso.compagny.Company;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.smstemplate.SmsTemplate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @ManyToOne(optional = true)
    private ExcelImportProfile profile;

    @ManyToOne(optional = true)
    private SmsTemplate template;

    @ManyToOne(optional = true)
    private ExcelVariable phoneVariable;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CampaignChannel channel = CampaignChannel.SMS;

    @Column(columnDefinition = "TEXT")
    private String messageTemplate;

    private String emailSubject;

    private String recipientColumn;

    private String mediaUrl;

    private String ctaLabel;

    private String ctaUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Builder.Default
    private Integer totalRecipients = 0;

    @Builder.Default
    private Integer totalValid = 0;

    @Builder.Default
    private Integer totalInvalid = 0;

    @Builder.Default
    private Integer totalSegments = 0;

    @Builder.Default
    private Integer totalSent = 0;

    @Builder.Default
    private Integer totalFailed = 0;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = CampaignStatus.DRAFT;
        }
        if (channel == null) {
            channel = CampaignChannel.SMS;
        }
    }

    public String resolvedTemplateContent() {
        if (messageTemplate != null && !messageTemplate.isBlank()) {
            return messageTemplate;
        }
        if (template != null && template.getContent() != null) {
            return template.getContent();
        }
        return "";
    }

    public CampaignChannel resolvedChannel() {
        return channel == null ? CampaignChannel.SMS : channel;
    }
}
