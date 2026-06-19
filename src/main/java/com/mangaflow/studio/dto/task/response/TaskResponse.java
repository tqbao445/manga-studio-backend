package com.mangaflow.studio.dto.task.response;

import com.mangaflow.studio.model.region.RegionType;
import com.mangaflow.studio.model.task.Priority;
import com.mangaflow.studio.model.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── TaskResponse ──
 * DTO trả về thông tin chi tiết của 1 task cho frontend.
 * <p>
 * 📌 Dùng ở tất cả các endpoint Task:
 *    - GET    /api/tasks                    → list (submissions/attachments = null)
 *    - GET    /api/tasks/{id}              → detail (kèm submissions/attachments)
 *    - GET    /api/regions/{regionId}/tasks → list (submissions/attachments = null)
 *    - POST   /api/regions/{regionId}/tasks → task vừa tạo
 *    - PUT    /api/tasks/{id}              → task đã cập nhật
 *    - PATCH  /api/tasks/{id}/status       → task sau đổi status
 * <p>
 * 📌 Lưu ý về submissions & attachments:
 *    - Ở các endpoint list (GET list): 2 field này = null (không load để tối ưu)
 *    - Ở endpoint detail (GET by id):  2 field này có dữ liệu
 *    - Frontend dựa vào presence của 2 field này để biết có cần hiển thị không
 */
@Data
@Builder
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của 1 task")
public class TaskResponse {

    private Long id;

    @Schema(description = "Danh sách regions được giao trong task")
    private List<RegionBasicDTO> regions;

    @Schema(description = "Loại vùng (override)", example = "CHARACTER")
    private RegionType regionType;

    @Schema(description = "Tiêu đề công việc", example = "Vẽ nhân vật chính panel 3")
    private String title;

    @Schema(description = "Mô tả chi tiết", example = "Cần vẽ nhân vật chính trong khung cảnh hành động")
    private String description;

    @Schema(description = "Ghi chú cho ASSISTANT", example = "Chú ý biểu cảm khuôn mặt")
    private String notes;

    @Schema(description = "URL ảnh tham khảo", example = "https://...ref.jpg")
    private String referenceImageUrl;

    @Schema(description = "URL ảnh của page (copy từ region)", example = "https://...page3.jpg")
    private String pageImageUrl;

    @Schema(description = "Trạng thái task: TODO, IN_PROGRESS, DONE, REJECTED", example = "TODO")
    private TaskStatus status;

    @Schema(description = "Mức ưu tiên: LOW, MEDIUM, HIGH, URGENT", example = "HIGH")
    private Priority priority;

    @Schema(description = "ASSISTANT được giao")
    private UserBasicDTO assistant;

    @Schema(description = "MANGAKA giao việc")
    private UserBasicDTO assignedBy;

    @Schema(description = "Thời điểm giao việc", example = "2026-05-30T10:00:00")
    private LocalDateTime assignedAt;

    @Schema(description = "Hạn chót", example = "2026-06-05T00:00:00")
    private LocalDateTime dueDate;

    @Schema(description = "Thời điểm tạo", example = "2026-05-30T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Lịch sử nộp bài (chỉ có ở GET detail)")
    private List<TaskSubmissionResponse> submissions;

    @Schema(description = "File đính kèm (chỉ có ở GET detail)")
    private List<TaskAttachmentResponse> attachments;
}
