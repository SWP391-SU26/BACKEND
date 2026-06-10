package com.courseqa.service;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.User;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private AuthDto.RegisterRequest registerRequest;
    private AuthDto.LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new AuthDto.RegisterRequest();
        registerRequest.fullName = "Khang Test";
        registerRequest.email = "khang@test.com";
        registerRequest.password = "123456";
        registerRequest.roleName = "STUDENT";

        loginRequest = new AuthDto.LoginRequest();
        loginRequest.email = "khang@test.com";
        loginRequest.password = "123456";
    }

    // Test 1: Register successfully
    @Test
    void register_ShouldReturnToken_WhenEmailNotExists() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(anyString())).thenReturn("fake-jwt-token");

        // Act
        AuthDto.AuthResponse response = authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals("fake-jwt-token", response.token);
        verify(userRepository, times(1)).save(any(User.class));
        verify(userRoleRepository, times(1)).save(any());
    }

    // Test 2: Register fails when email already exists
    @Test
    void register_ShouldThrowException_WhenEmailAlreadyExists() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.register(registerRequest);
        });

        assertEquals("Email already in use", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    // Test 3: Login successfully
    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        // Arrange
        User mockUser = new User();
        mockUser.setEmail("khang@test.com");
        mockUser.setPasswordHash("hashedPassword");
        mockUser.setIsActive(true);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(anyString())).thenReturn("fake-jwt-token");

        // Act
        AuthDto.AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("fake-jwt-token", response.token);
    }

    // Test 4: Login fails with wrong password
    @Test
    void login_ShouldThrowException_WhenPasswordIsWrong() {
        // Arrange
        User mockUser = new User();
        mockUser.setEmail("khang@test.com");
        mockUser.setPasswordHash("hashedPassword");
        mockUser.setIsActive(true);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals("Invalid email or password", exception.getMessage());
    }

    // Test 5: Login fails when user not found
    @Test
    void login_ShouldThrowException_WhenEmailNotFound() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            authService.login(loginRequest);
        });
    }
}