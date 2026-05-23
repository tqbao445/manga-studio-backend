package com.mangaflow.studio.auth.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String displayName;
    private String avatarUrl;
    private String bio;
}
