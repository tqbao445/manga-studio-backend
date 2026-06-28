package com.mangaflow.studio.dto.dashboard.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── NudgeRequest ──
 * Request body cho POST /api/v1/dashboard/nudge/{authorId}.
 * <p>
 * Tantou Editor gửi yêu cầu này khi click nút "Quick Nudge"
 * trên Late Studios Alert để nhắc nhở Mangaka về chapter sắp
 * trễ deadline.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Flow:
 * ══════════════════════════════════════════════════════════════════
 *  1. Tantou thấy cảnh báo chậm (LateStudioAlertItem)
 *  2. Click "Quick Nudge"
 *  3. FE hiện confirm dialog (có thể sửa message mặc định)
 *  4. FE gửi POST /api/v1/dashboard/nudge/{authorId}
 *     Body: { chapterId, message }
 *  5. Backend kiểm tra quyền, tạo Notification, push WebSocket
 *  6. Mangaka nhận được notification realtime
 */
@Data
@Schema(description = "Yêu cầu gửi nhắc nhở (Nudge) cho Mangaka")
public class NudgeRequest {

    @NotNull(message = "chapterId is required")
    @Schema(description = "ID của chapter cần nhắc", example = "42")
    private Long chapterId;

    @NotBlank(message = "message is required")
    @Schema(description = "Nội dung nhắc nhở",
            example = "Your chapter deadline is approaching. Please pick up the pace!")
    private String message;
}
