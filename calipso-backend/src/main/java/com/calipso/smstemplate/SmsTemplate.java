package com.calipso.smstemplate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.calipso.compagny.Company;
import com.calipso.importprofile.ExcelImportProfile;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sms_templates")
public class SmsTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @ManyToOne(optional = false)
    private ExcelImportProfile profile;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (active == null) {
            active = true;
        }
    }
}