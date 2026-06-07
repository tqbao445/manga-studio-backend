package com.mangaflow.studio.dto.meeting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DecisionRequest {

    @Schema(description = "Quyết định: APPROVED (duyệt) hoặc REJECTED (từ chối)", example = "APPROVED")
    @NotBlank(message = "Quyết định (APPROVED/REJECTED) là bắt buộc")
    private String decision;
}
