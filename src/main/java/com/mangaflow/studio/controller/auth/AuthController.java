package com.mangaflow.studio.controller.auth;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.auth.request.LoginRequest;
import com.mangaflow.studio.dto.auth.request.RegisterRequest;
import com.mangaflow.studio.dto.auth.request.UpdateProfileRequest;
import com.mangaflow.studio.dto.auth.response.AuthResponse;
import com.mangaflow.studio.dto.auth.response.UserDTO;
import com.mangaflow.studio.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserDTO userDTO = userMapper.toDTO(userDetails.getUser());
        return ResponseEntity.ok(userDTO);
    }

    @PatchMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        UserDTO result = authService.updateProfile(userDetails.getUser(), request);
        return ResponseEntity.ok(result);
    }
}
