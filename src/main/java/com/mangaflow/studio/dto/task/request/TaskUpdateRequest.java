package com.mangaflow.studio.dto.task.request;

import com.mangaflow.studio.model.region.RegionType;
import com.mangaflow.studio.model.task.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request cập nhật task — tất cả field đều optional")
public class TaskUpdateRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Tiêu đề công việc", example = "Vẽ nhân vật chính panel 3 (sửa)")
    private String title;

    @Schema(description = "Loại vùng (override region type)", example = "CHARACTER")
    private RegionType regionType;

    @Schema(description = "ID của ASSISTANT được giao (null = giữ nguyên)", example = "5")
    private Long assistantId;

    @Schema(description = "Mức ưu tiên: LOW, MEDIUM, HIGH, URGENT", example = "HIGH")
    private Priority priority;

    @Schema(description = "Mô tả chi tiết công việc", example = "Cần vẽ nhân vật chính trong khung cảnh hành động")
    private String description;

    @Schema(description = "Ghi chú cho ASSISTANT", example = "Tham khảo file reference đính kèm")
    private String notes;

    @Schema(description = "URL ảnh tham khảo", example = "https://res.cloudinary.com/.../reference.jpg")
    private String referenceImageUrl;

    @Schema(description = "Hạn chót (ISO datetime)", example = "2026-06-05T00:00:00")
    private LocalDateTime dueDate;
}
