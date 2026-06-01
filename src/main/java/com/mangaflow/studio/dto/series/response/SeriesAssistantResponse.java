package com.mangaflow.studio.dto.series.response;

import com.mangaflow.studio.dto.auth.response.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── SeriesAssistantResponse ──
 * DTO trả về thông tin lời mời / thành viên assistant trong series.
 * <p>
 * 📌 Dùng trong các endpoint:
 *    GET  /api/series/{seriesId}/assistants    → danh sách ACCEPTED
 *    POST /api/series/{seriesId}/assistants/invite → kết quả mời
 *    GET  /api/assistants/invitations          → danh sách PENDING
 *    PATCH /api/assistants/invitations/{id}    → kết quả phản hồi
 * <p>
 * 📌 @Data (Lombok): getter, setter, equals, hashCode, toString.
 * 📌 @Builder: pattern Builder để tạo DTO linh hoạt.
 * 📌 @AllArgsConstructor: constructor với tất cả field.
 */
@Data
@Builder
@AllArgsConstructor
public class SeriesAssistantResponse {

    /**
     * id: ID của record trong bảng series_assistant.
     * Dùng khi assistant muốn accept/reject (gửi lên PATCH).
     */
    private Long id;

    /**
     * seriesId: ID của series liên quan.
     * Để frontend có thể link tới SeriesDetailPage.
     */
    private Long seriesId;

    /**
     * assistant: Thông tin cơ bản của assistant (id, displayName, avatarUrl).
     * Dùng UserDTO — không chứa password hay email nhạy cảm.
     * Map tự động nhờ MapStruct + UserMapper.
     */
    private UserDTO assistant;

    /**
     * invitedBy: Thông tin người đã mời (MANGAKA).
     * Hiển thị trong danh sách lời mời của assistant:
     *   "Tran Van A đã mời bạn tham gia series 'Neon Horizon'"
     */
    private UserDTO invitedBy;

    /**
     * status: Trạng thái lời mời hiện tại.
     * String (không phải enum) để frontend dễ xử lý:
     *   "PENDING" | "ACCEPTED" | "REJECTED"
     */
    private String status;

    /**
     * invitedAt: Thời điểm gửi lời mời.
     * Hiển thị: "Đã mời 2 ngày trước"
     */
    private LocalDateTime invitedAt;

    /**
     * respondedAt: Thời điểm assistant phản hồi.
     * NULL nếu chưa phản hồi (PENDING).
     * Hiển thị: "Đã đồng ý 1 ngày trước" / "Đã từ chối 3 ngày trước"
     */
    private LocalDateTime respondedAt;
}
