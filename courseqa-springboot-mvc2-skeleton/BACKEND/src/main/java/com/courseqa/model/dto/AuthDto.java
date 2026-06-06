package com.courseqa.model.dto;

import com.courseqa.model.entity.User;
import java.util.List;
import java.util.UUID;

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
        public UserResponse user;

        public AuthResponse(String token, User user, List<String> roles) {
            this.token = token;
            this.user = new UserResponse(user, roles);
        }
    }

    public static class UserResponse {
        public UUID userId;
        public String fullName;
        public String email;
        public List<String> roles;

        public UserResponse(User user, List<String> roles) {
            this.userId = user.getUserId();
            this.fullName = user.getFullName();
            this.email = user.getEmail();
            this.roles = roles;
        }
    }
}
