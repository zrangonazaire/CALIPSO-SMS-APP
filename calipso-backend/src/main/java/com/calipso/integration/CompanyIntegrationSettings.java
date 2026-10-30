package com.calipso.integration;

import com.calipso.compagny.Company;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_integration_settings", uniqueConstraints = @UniqueConstraint(columnNames = "company_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyIntegrationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    private String smsSenderAddress;
    private String orangeSenderName;
    @Column(length = 2048)
    private String orangeClientIdEncrypted;
    @Column(length = 2048)
    private String orangeClientSecretEncrypted;

    private String whatsappPhoneNumberId;
    private String whatsappApiUrl;
    @Column(length = 4096)
    private String whatsappAccessTokenEncrypted;

    private String smtpHost;
    private Integer smtpPort;
    private String smtpFrom;
    private String smtpUsername;
    @Column(length = 2048)
    private String smtpPasswordEncrypted;
    private Boolean smtpStartTls;
}
