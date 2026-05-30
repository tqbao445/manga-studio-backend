package com.mangaflow.studio.dto.auth.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String displayName;
    private String avatarUrl;
    private String bio;
}
