package com.courseqa.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.AuthDto;
import com.courseqa.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
    @Autowired 
    private AuthService authService;

    @PostMapping("/register")
     public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> register(
        @RequestBody AuthDto.RegisterRequest request) {
        try {
            AuthDto.AuthResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
           return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
}
        }


 @PostMapping("/login")
     public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> login(
        @RequestBody AuthDto.LoginRequest request) {
        try {
            AuthDto.AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
           return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
}
        }

        @PostMapping("/logout/{userId}")
        public ResponseEntity<ApiResponse<String>> logout
        (@PathVariable UUID userId){
           try{
            authService.logout(userId);
            return ResponseEntity.ok(ApiResponse.ok("Logout successful"));
           }catch(Exception e){
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
           }
        
    }

@GetMapping("/users")
public ResponseEntity<ApiResponse<?>> getAllUsers(){
   try{
return ResponseEntity.ok(ApiResponse.ok("Users endpoint - coming soon"));
   }catch(RuntimeException e){
    return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
   }
}


@GetMapping("/users/{userId}/roles")
public ResponseEntity<ApiResponse<?>> getUserRoles(
    @PathVariable UUID userId){
   try{
     return ResponseEntity.ok(ApiResponse.ok("Roles endpoint - coming soon"));

   }catch(RuntimeException e){
    return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
   }
    }
}