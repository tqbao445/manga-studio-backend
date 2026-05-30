package com.mangaflow.studio.dto.auth.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String email;
    private String username;
    private String displayName;
    private String role;
    private String avatarUrl;
    private String bio;
}
