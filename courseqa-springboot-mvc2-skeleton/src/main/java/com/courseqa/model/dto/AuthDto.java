package com.courseqa.model.dto;

import com.courseqa.model.entity.User;

// DTOs for register, login, logout.
// TODO: Add request/response DTO classes here.

public class AuthDto {
public static class RegisterRequest {
        public String fullName;
        public String email;
        public String password;
        public String roleName = "STUDENT";
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class AuthResponse {
        public String token;
        public User user;

        public AuthResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }
    }
}
