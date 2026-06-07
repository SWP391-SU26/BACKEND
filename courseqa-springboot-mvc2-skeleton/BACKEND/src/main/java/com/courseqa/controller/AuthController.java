package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.AuthDto;
import com.courseqa.service.AuthService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDto.AuthResponse> register(@RequestBody AuthDto.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDto.AuthResponse> login(@RequestBody AuthDto.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout/{userId}")
    public ApiResponse<Void> logout(@PathVariable UUID userId) {
        authService.logout(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/users")
    public ApiResponse<List<AuthDto.UserResponse>> getUsers() {
        return ApiResponse.ok(authService.getUsers());
    }

    @GetMapping("/users/{userId}/roles")
    public ApiResponse<List<String>> getRoles(@PathVariable UUID userId) {
        return ApiResponse.ok(authService.getRoles(userId));
    }
}
