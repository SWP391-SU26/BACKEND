package com.courseqa.security;

import static org.junit.jupiter.api.Assertions.*;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWM=";

    @Test
    void issuedTokenContainsSignedIdentityAndRoles() {
        JwtService service = new JwtService(SECRET, 30);
        UUID userId = UUID.randomUUID();
        String token = service.issue(userId, "admin@example.com", List.of("ADMIN"));
        JwtPrincipal principal = service.parse(token);
        assertEquals(userId, principal.userId());
        assertEquals("admin@example.com", principal.email());
        assertEquals(List.of("ADMIN"), principal.roles());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService issuer = new JwtService(SECRET, 30);
        JwtService verifier = new JwtService("QW5vdGhlci10ZXN0LXNlY3JldC10aGF0LWlzLWxvbmcgZW5vdWdoLWZvci1obWFj", 30);
        String token = issuer.issue(UUID.randomUUID(), "student@example.com", List.of("STUDENT"));
        assertThrows(JwtException.class, () -> verifier.parse(token));
    }
}
