package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.companyuser.CompanyUserRole;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int TOKEN_BYTES = 48;
    private static final int TOKEN_TTL_HOURS = 12;

    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginResponse login(LoginRequest request) {
        CompanyUser user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));

        if (!Boolean.TRUE.equals(user.getActive()) || user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        return issueToken(user);
    }

    public LoginResponse setupAdmin(SetupAdminRequest request) {
        if (userRepository.existsByPasswordHashIsNotNull()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "L'initialisation est deja effectuee");
        }

        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce nom d'utilisateur existe deja");
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.companyName())
                .email(request.email())
                .smsBalance(0)
                .active(true)
                .build());

        CompanyUser user = userRepository.save(CompanyUser.builder()
                .company(company)
                .fullName(request.fullName())
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(CompanyUserRole.ADMIN)
                .active(true)
                .build());

        return issueToken(user);
    }

    public CompanyUser authenticateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(AuthToken::isActive)
                .map(AuthToken::getUser)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElse(null);
    }

    public void logout(String token) {
        tokenRepository.findByToken(token).ifPresent(authToken -> {
            authToken.setRevokedAt(LocalDateTime.now());
            tokenRepository.save(authToken);
        });
    }

    private LoginResponse issueToken(CompanyUser user) {
        AuthToken authToken = AuthToken.builder()
                .token(generateToken())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS))
                .build();

        AuthToken saved = tokenRepository.save(authToken);
        return new LoginResponse(saved.getToken(), saved.getExpiresAt(), AuthUserResponse.from(user));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
