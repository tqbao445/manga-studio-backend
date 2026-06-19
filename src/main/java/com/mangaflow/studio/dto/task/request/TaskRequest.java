package com.mangaflow.studio.dto.task.request;

import com.mangaflow.studio.model.region.RegionType;
import com.mangaflow.studio.model.task.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── TaskRequest ──
 * DTO nhận dữ liệu từ frontend khi tạo mới hoặc cập nhật task.
 * <p>
 * 📌 Dùng ở:
 *    - POST /api/regions/{regionId}/tasks → tạo task mới (endpoint 4)
 *    - PUT  /api/tasks/{id}               → cập nhật task (endpoint 5)
 * <p>
 * 📌 Validation:
 *    - title:       bắt buộc, max 255 ký tự
 *    - assistantId: bắt buộc, phải là ID của user có role ASSISTANT
 *    - priority:    không bắt buộc, mặc định MEDIUM
 *    - regionType:  không bắt buộc (override từ region)
 *    - description: không bắt buộc
 *    - notes:       không bắt buộc
 *    - referenceImageUrl: không bắt buộc
 *    - dueDate:     không bắt buộc, phải ở tương lai
 * <p>
 * 📌 Khi update (PUT):
 *    Tất cả field đều optional — không gửi field nào → giữ nguyên giá trị cũ.
 *    Dùng chung DTO này với create, Service tự xử lý null.
 */
@Data
@Schema(description = "Request tạo hoặc cập nhật task")
public class TaskRequest {

    /**
     * title: Tiêu đề công việc.
     * Bắt buộc — không được để trống, tối đa 255 ký tự.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Tiêu đề công việc", example = "Vẽ nhân vật chính panel 3", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * regionType: Loại vùng override.
     * Không bắt buộc — nếu không gửi, regionType lấy từ region gốc.
     * <p>
     * Giá trị hợp lệ: BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER
     */
    @Schema(description = "Danh sách region IDs được giao trong task này", example = "[1, 2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> regionIds;

    /**
     * regionType: Loại vùng override.
     * Không bắt buộc — nếu không gửi, regionType lấy từ region gốc.
     * <p>
     * Giá trị hợp lệ: BACKGROUND, CHARACTER, TEXT, EFFECT, TONE, OTHER
     */
    @Schema(description = "Loại vùng (override region type)", example = "CHARACTER")
    private RegionType regionType;

    /**
     * assistantId: ID của ASSISTANT được giao việc.
     * Bắt buộc — phải là ID của user có role ASSISTANT.
     */
    @NotNull(message = "Assistant ID is required")
    @Schema(description = "ID của ASSISTANT được giao", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assistantId;

    /**
     * priority: Mức độ ưu tiên.
     * Không bắt buộc — mặc định MEDIUM nếu không gửi.
     * <p>
     * Giá trị hợp lệ: LOW, MEDIUM, HIGH, URGENT
     */
    @Schema(description = "Mức ưu tiên: LOW, MEDIUM, HIGH, URGENT", example = "HIGH")
    private Priority priority;

    /**
     * description: Mô tả chi tiết công việc.
     * Không bắt buộc.
     */
    @Schema(description = "Mô tả chi tiết công việc", example = "Cần vẽ nhân vật chính trong khung cảnh hành động, chú ý ánh sáng và biểu cảm")
    private String description;

    /**
     * notes: Ghi chú cho ASSISTANT.
     * Không bắt buộc.
     */
    @Schema(description = "Ghi chú cho ASSISTANT", example = "Tham khảo file reference đính kèm")
    private String notes;

    /**
     * referenceImageUrl: URL ảnh tham khảo.
     * Không bắt buộc.
     */
    @Schema(description = "URL ảnh tham khảo", example = "https://res.cloudinary.com/.../reference.jpg")
    private String referenceImageUrl;

    /**
     * dueDate: Hạn chót.
     * Không bắt buộc — định dạng ISO datetime.
     * Nếu gửi → phải ở tương lai (validate ở Service).
     */
    @Schema(description = "Hạn chót (ISO datetime)", example = "2026-06-05T00:00:00")
    private LocalDateTime dueDate;
}
