package com.mangaflow.studio.dto.task.mapper;

import com.mangaflow.studio.dto.task.response.TaskAttachmentResponse;
import com.mangaflow.studio.model.task.TaskAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ── TaskAttachmentMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity TaskAttachment và TaskAttachmentResponse DTO.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Mapping đặc biệt:
 * ════════════════════════════════════════════════════════════════
 *  taskId: TaskAttachment.task.id → TaskAttachmentResponse.taskId
 *  → Dùng @Mapping để MapStruct biết lấy id từ task entity
 * <p>
 *  Các field còn lại (id, fileUrl, uploadedAt) tự động map vì cùng tên.
 */
@Mapper(componentModel = "spring")
public interface TaskAttachmentMapper {

    /**
     * Chuyển đổi TaskAttachment entity → TaskAttachmentResponse DTO.
     * <p>
     * 📌 @Mapping(target = "taskId", source = "task.id"):
     *    Lấy task.id từ entity và gán vào taskId của DTO.
     *
     * @param attachment Entity từ database
     * @return TaskAttachmentResponse DTO
     */
    @Mapping(target = "taskId", source = "task.id")
    TaskAttachmentResponse toResponse(TaskAttachment attachment);
}
