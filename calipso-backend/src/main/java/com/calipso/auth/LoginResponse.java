package com.calipso.auth;

import java.time.LocalDateTime;

public record LoginResponse(
        String token,
        LocalDateTime expiresAt,
        AuthUserResponse user
) {
}
