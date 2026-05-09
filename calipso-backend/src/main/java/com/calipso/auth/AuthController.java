package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/setup")
    public LoginResponse setup(@RequestBody @Valid SetupAdminRequest request) {
        return authService.setupAdmin(request);
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal CompanyUser user) {
        return AuthUserResponse.from(user);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization == null ? "" : authorization.replace("Bearer ", "").trim();
        authService.logout(token);
    }
}
