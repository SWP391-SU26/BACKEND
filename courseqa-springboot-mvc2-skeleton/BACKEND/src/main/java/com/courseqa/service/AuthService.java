package com.courseqa.service;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.User;
import com.courseqa.model.entity.UserRole;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.security.JwtService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "USER", "TEACHER", "STUDENT", "RESEARCHER");
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SubscriptionService subscriptionService;
    private final String mailFrom;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;

    public AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            SubscriptionService subscriptionService,
            @Value("${app.mail.from:no-reply@courseqa.local}") String mailFrom,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailSenderProvider = mailSenderProvider;
        this.subscriptionService = subscriptionService;
        this.mailFrom = mailFrom;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
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
        //role.setRoleName(normalizeRole(request.roleName));
        role.setRoleName("STUDENT");
        role.setPermissionJson("{}");
        role.setAssignedAt(now);
        role.setIsActive(true);
        userRoleRepository.save(role);
        subscriptionService.ensureFreeSubscription(savedUser.getUserId());

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

    @Transactional
    public void forgotPassword(AuthDto.ForgotPasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forgot password request is required.");
        }

        String email = normalizeEmail(request.email);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            return;
        }

        if (isBlank(mailHost) || isBlank(mailUsername) || isBlank(mailPassword)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured.");
        }

        String newPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            sendNewPasswordEmail(mailSender, user, newPassword);
        } catch (MailException error) {
            LOGGER.warn("Could not send reset password email to {}.", user.getEmail(), error);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send reset password email.");
        }
    }

    public void logout(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        user.setLastLogoutAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, AuthDto.ChangePasswordRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change password request is required.");
        }
        if (isBlank(request.currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required.");
        }
        if (isBlank(request.newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required.");
        }
        if (request.newPassword.trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 6 characters.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (!passwordEncoder.matches(request.currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword));
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
        List<String> roles = getRoleNames(user.getUserId());
        return new AuthDto.AuthResponse(jwtService.issue(user.getUserId(), user.getEmail(), roles), user, roles);
    }

    private List<String> getRoleNames(UUID userId) {
        return userRoleRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(UserRole::getRoleName)
                .toList();
    }

    public boolean isAdmin(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester user not found.");
        }
        return getRoleNames(userId).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role));
    }

    private String generateRandomPassword() {
        StringBuilder password = new StringBuilder();
        for (int index = 0; index < 12; index++) {
            password.append(PASSWORD_CHARS.charAt(PASSWORD_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private void sendNewPasswordEmail(JavaMailSender mailSender, User user, String newPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("FStu password reset");
        message.setText("""
                Hello %s,

                We received a forgot password request for your FStu account.

                Your new temporary password is:
                %s

                You can now log in with this password.
                If you did not request this, please contact the system administrator.
                """.formatted(user.getFullName(), newPassword));
        mailSender.send(message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
