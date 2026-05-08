package com.calipso.excelvariable;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.calipso.compagny.Company;
import com.calipso.config.DataType;
import com.calipso.importprofile.ExcelImportProfile;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "excel_variables",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_profile_variable_code", columnNames = {"profile_id", "code"})
        }
)
public class ExcelVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profile_id")
    private ExcelImportProfile profile;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataType dataType;

    private Boolean required = false;

    private Boolean phone = false;

    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();

        if (required == null) {
            required = false;
        }

        if (phone == null) {
            phone = false;
        }

        if (active == null) {
            active = true;
        }

        if (code != null) {
            code = code.trim().toUpperCase().replace(" ", "_");
        }
    }
}
