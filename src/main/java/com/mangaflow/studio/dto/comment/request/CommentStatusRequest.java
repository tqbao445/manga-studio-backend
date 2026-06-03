package com.mangaflow.studio.dto.comment.request;

import com.mangaflow.studio.model.comment.CommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ── CommentStatusRequest ──
 * DTO nhận dữ liệu từ Frontend khi đổi trạng thái comment.
 *
 * 📌 Dùng trong:
 *    - PATCH /api/v1/comments/{id}/status — resolve/reopen comment
 *
 * ══════════════════════════════════════════════════════════════════
 *  Chỉ có 1 field duy nhất: status
 * ══════════════════════════════════════════════════════════════════
 *
 *  📌 ACTIVE → RESOLVED:   Đánh dấu đã giải quyết xong
 *     (Tantou Editor hoặc Mangaka xác nhận vấn đề đã xử lý)
 *
 *  📌 RESOLVED → ACTIVE:   Mở lại comment để thảo luận tiếp
 *     (Khi cần chỉnh sửa thêm hoặc chưa ưng ý)
 *
 * ══════════════════════════════════════════════════════════════════
 *  Ví dụ request body:
 * ══════════════════════════════════════════════════════════════════
 *  {
 *      "status": "RESOLVED"
 *  }
 *
 *  => Chuyển comment từ ACTIVE → RESOLVED (đã xong)
 */
@Data
@Schema(description = "Request đổi trạng thái Comment (ACTIVE ↔ RESOLVED)")
public class CommentStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(
            description = "Trạng thái mới của comment: ACTIVE (đang hoạt động) hoặc RESOLVED (đã giải quyết)",
            example = "RESOLVED",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private CommentStatus status;
}
