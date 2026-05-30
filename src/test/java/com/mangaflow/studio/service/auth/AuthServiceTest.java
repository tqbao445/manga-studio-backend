package com.mangaflow.studio.service.auth;

import com.mangaflow.studio.common.security.JwtUtil;
import com.mangaflow.studio.dto.auth.mapper.UserMapper;
import com.mangaflow.studio.dto.auth.request.LoginRequest;
import com.mangaflow.studio.dto.auth.response.AuthResponse;
import com.mangaflow.studio.dto.auth.response.UserDTO;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserMapper mapper;


    @InjectMocks
    private AuthService authService;

    private LoginRequest loginRequest;
    private User user;
    private UserDTO userDTO;

    @BeforeEach
    void setup() {
        loginRequest = LoginRequest.builder()
                .email("user@gmail.com")
                .password("password")
                .build();

        user = User.builder()
                .id(1L)
                .username("mangaka")
                .email("user@gmail.com")
                .password("encode password")
                .role(Role.MANGAKA)
                .bio("Bio")
                .build();

        userDTO = UserDTO.builder()
                .username("mangaka")
                .email("user@gmail.com")
                .role(Role.MANGAKA.name())
                .bio("Bio")
                .build();
    }

    @Test
    public void login_should_success() {
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches(loginRequest.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name())).thenReturn("accessToken");
        when(mapper.toDTO(user)).thenReturn(userDTO);

        AuthResponse authResponse = authService.login(loginRequest);

        assertEquals("accessToken", authResponse.getAccessToken());
        assertEquals("mangaka", authResponse.getUser().getUsername());

        verify(userRepository, times(1)).findByEmail(any());

        verify(encoder, times(1)).matches(any(), any());

        verify(jwtUtil, times(1)).generateToken(any(), any(), any());

        verify(mapper, times(1)).toDTO(any());

    }

}