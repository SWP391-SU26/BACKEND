package com.courseqa.service;


import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.courseqa.model.dto.AuthDto;
import com.courseqa.model.entity.User;
import com.courseqa.model.entity.UserRole;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.util.JwtUtil;

@Service
public class AuthService {
    // Register, login, logout, password hashing, role loading.
    // TODO: Add business logic here.

    @Autowired
    private UserRepository userRepository;
    @Deprecated
    private UserRoleRepository userRoleRepository;
    @Autowired
    private JwtUtil jwtUtil;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    //Register method
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if(userRepository.existsByEmail(request.email)){
            throw new RuntimeException("Email already in use");
        }
    
   //Create new user
    User user = new User();
     user.setFullName(request.fullName);
     user.setEmail(request.email);
     user.setPasswordHash(passwordEncoder.encode(request.password));
     user.setIsActive(true);
     user.setCreatedAt(LocalDateTime.now());
     user.setUpdatedAt(LocalDateTime.now());
     userRepository.save(user);

 //Assign default role
    UserRole role = new UserRole();
     role.setUserId(user.getUserId());
     role.setRoleName(request.roleName);
     role.setIsActive(true);
     userRoleRepository.save(role);


    //Gennerate JWT token
    String token = jwtUtil.generateToken(user.getEmail());
    return new AuthDto.AuthResponse(token, user);
    }

//Login method
public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
    User user = userRepository.findByEmail(request.email)
    .orElseThrow(() -> new RuntimeException("Invalid email or password"));

    if(!passwordEncoder.matches(request.password, user.getPasswordHash())){
        throw new RuntimeException("Invalid email or password");
    }
    if(!user.getIsActive()){
        throw new RuntimeException("User account is dissable");
    }
    user.setLastLoginAt(LocalDateTime.now());
    userRepository.save(user);

    String token = jwtUtil.generateToken(user.getEmail());
    return new AuthDto.AuthResponse(token, user);
}

//Logout method
public void logout(UUID userId){

    if(userId == null){
        throw new RuntimeException("User ID is required");
    }

    User user = userRepository.findById(userId)
    .orElseThrow(() -> new RuntimeException("User not found"));

    user.setLastLogoutAt(LocalDateTime.now());
    userRepository.save(user);
}


}