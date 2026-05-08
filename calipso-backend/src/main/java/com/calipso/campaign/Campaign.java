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

    @ManyToOne(optional = false)
    private ExcelImportProfile profile;

    @ManyToOne(optional = false)
    private SmsTemplate template;

    @ManyToOne(optional = false)
    private ExcelVariable phoneVariable;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.DRAFT;

    private Integer totalRecipients = 0;

    private Integer totalValid = 0;

    private Integer totalInvalid = 0;

    private Integer totalSegments = 0;

    private Integer totalSent = 0;

    private Integer totalFailed = 0;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = CampaignStatus.DRAFT;
        }
    }
}
