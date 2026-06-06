package com.mangaflow.studio.dto.series.response;

import com.mangaflow.studio.dto.auth.response.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ── SeriesTantouResponse ──
 * DTO trả về thông tin lời mời tantou tham gia kiểm duyệt series.
 * <p>
 * 📌 Dùng trong các endpoint:
 *    POST   /api/series/{seriesId}/tantou/invite     → kết quả mời
 *    GET    /api/series/{seriesId}/tantou/invitations → danh sách lời mời
 *    GET    /api/tantou/invitations                   → danh sách PENDING
 *    PATCH  /api/tantou/invitations/{id}              → kết quả phản hồi
 * <p>
 * 📌 @Data (Lombok): getter, setter, equals, hashCode, toString.
 * 📌 @Builder: pattern Builder để tạo DTO linh hoạt.
 * 📌 @AllArgsConstructor: constructor với tất cả field.
 */
@Data
@Builder
@AllArgsConstructor
public class SeriesTantouResponse {

    /**
     * id: ID của record trong bảng series_tantou_invitation.
     * Dùng khi tantou muốn accept/reject (gửi lên PATCH).
     */
    private Long id;

    /**
     * seriesId: ID của series liên quan.
     * Để frontend có thể link tới SeriesDetailPage.
     */
    private Long seriesId;

    /**
     * tantou: Thông tin cơ bản của tantou (id, displayName, avatarUrl).
     * Dùng UserDTO — không chứa password hay email nhạy cảm.
     */
    private UserDTO tantou;

    /**
     * invitedBy: Thông tin người đã mời (MANGAKA).
     * Hiển thị trong danh sách lời mời của tantou:
     *   "Tran Van A đã mời bạn duyệt series 'Neon Horizon'"
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
     * respondedAt: Thời điểm tantou phản hồi.
     * NULL nếu chưa phản hồi (PENDING).
     * Hiển thị: "Đã đồng ý 1 ngày trước" / "Đã từ chối 3 ngày trước"
     */
    private LocalDateTime respondedAt;
}
