package com.calipso.importprofile;




import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.calipso.compagny.Company;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "excel_import_profiles")
public class ExcelImportProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    private String description;

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