package com.calipso.companyuser;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/company-users")
@RequiredArgsConstructor
public class CompanyUserController {

    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;

    @PostMapping
    public CompanyUser create(@RequestBody @Valid CreateCompanyUserRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new RuntimeException("Ce nom d'utilisateur existe deja");
        }

        CompanyUser user = CompanyUser.builder()
                .company(company)
                .fullName(request.fullName())
                .username(request.username())
                .email(request.email())
                .phone(request.phone())
                .role(request.role() == null ? CompanyUserRole.OPERATOR : request.role())
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @GetMapping("/company/{companyId}")
    public List<CompanyUser> findByCompany(@PathVariable Long companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @PatchMapping("/{userId}/active")
    public CompanyUser updateStatus(@PathVariable Long userId, @RequestParam boolean active) {
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        user.setActive(active);
        return userRepository.save(user);
    }
}
