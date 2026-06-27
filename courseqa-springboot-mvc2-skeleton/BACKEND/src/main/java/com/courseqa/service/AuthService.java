package com.courseqa.service;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.User;
import com.courseqa.model.entity.UserRole;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "USER", "TEACHER", "STUDENT", "RESEARCHER");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Register request is required.");
        }
        String email = normalizeEmail(request.email);

        if (request.fullName == null || request.fullName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required.");
        }

        if (request.password == null || request.password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setFullName(request.fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);
        UserRole role = new UserRole();
        role.setUserId(savedUser.getUserId());
        role.setRoleName(normalizeRole(request.roleName));
        role.setPermissionJson("{}");
        role.setAssignedAt(now);
        role.setIsActive(true);
        userRoleRepository.save(role);

        return buildAuthResponse(savedUser);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login request is required.");
        }
        String email = normalizeEmail(request.email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is inactive.");
        }

        if (request.password == null || !passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    public void logout(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        user.setLastLogoutAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<AuthDto.UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> new AuthDto.UserResponse(user, getRoleNames(user.getUserId())))
                .toList();
    }

    public List<String> getRoles(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        return getRoleNames(userId);
    }

    @Transactional
    public AuthDto.UserResponse updateUserRole(UUID userId, AuthDto.UpdateUserRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        String roleName = normalizeRole(request == null ? null : request.roleName);
        LocalDateTime now = LocalDateTime.now();

        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        for (UserRole role : roles) {
            role.setIsActive(false);
        }
        userRoleRepository.saveAll(roles);

        UserRole role = new UserRole();
        role.setUserId(userId);
        role.setRoleName(roleName);
        role.setPermissionJson(roleName.equals("ADMIN") ? "{\"all\":true}" : "{}");
        role.setAssignedAt(now);
        role.setIsActive(true);
        userRoleRepository.save(role);

        user.setUpdatedAt(now);
        userRepository.save(user);

        return new AuthDto.UserResponse(user, getRoleNames(userId));
    }

    @Transactional
    public void deleteUser(UUID userId, UUID requesterId) {
        if (userId == null || requesterId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and requesterId are required.");
        }
        if (userId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot delete their own account.");
        }
        if (!isAdmin(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can delete users.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        userRepository.delete(user);
    }

    private AuthDto.AuthResponse buildAuthResponse(User user) {
        return new AuthDto.AuthResponse(user.getUserId().toString(), user, getRoleNames(user.getUserId()));
    }

    private List<String> getRoleNames(UUID userId) {
        return userRoleRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(UserRole::getRoleName)
                .toList();
    }

    private boolean isAdmin(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester user not found.");
        }
        return getRoleNames(userId).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String roleName) {
        String normalizedRole = roleName == null || roleName.isBlank()
                ? "STUDENT"
                : roleName.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role.");
        }

        return normalizedRole.equals("USER") ? "STUDENT" : normalizedRole;
    }
}
