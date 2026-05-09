package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_tokens", indexes = {
        @Index(name = "idx_auth_token_value", columnList = "token", unique = true)
})
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @ManyToOne(optional = false)
    private CompanyUser user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}
