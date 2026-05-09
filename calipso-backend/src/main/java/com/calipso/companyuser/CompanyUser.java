package com.calipso.companyuser;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "company_users")
public class CompanyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column
    private String passwordHash;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyUserRole role = CompanyUserRole.OPERATOR;

    @Column(nullable = false)
    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
        if (role == null) {
            role = CompanyUserRole.OPERATOR;
        }
    }
}
