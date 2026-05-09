package com.calipso.compagny;



import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    private String senderPhone;

    private String address;

    private String contactName;

    private String businessType;

    @Column(nullable = false)
    private Integer smsBalance = 0;

    @Column(nullable = false)
    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (smsBalance == null) {
            smsBalance = 0;
        }

        if (active == null) {
            active = true;
        }
    }
}
