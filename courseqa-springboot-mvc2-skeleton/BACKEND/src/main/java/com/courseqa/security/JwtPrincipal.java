package com.courseqa.security;

import java.util.List;
import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, List<String> roles) { }
