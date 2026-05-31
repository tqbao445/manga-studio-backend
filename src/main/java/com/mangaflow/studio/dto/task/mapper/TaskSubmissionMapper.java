package com.mangaflow.studio.dto.task.mapper;

import com.mangaflow.studio.dto.task.response.TaskSubmissionResponse;
import com.mangaflow.studio.model.task.TaskSubmission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ── TaskSubmissionMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity TaskSubmission và TaskSubmissionResponse DTO.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Mapping đặc biệt:
 * ════════════════════════════════════════════════════════════════
 *  taskId: TaskSubmission.task.id → TaskSubmissionResponse.taskId
 *  → Dùng @Mapping để MapStruct biết lấy id từ task entity
 * <p>
 *  Các field còn lại (id, resultImageUrl, fileUrl, note, version, status, submittedAt)
 *  tự động map vì cùng tên.
 */
@Mapper(componentModel = "spring")
public interface TaskSubmissionMapper {

    /**
     * Chuyển đổi TaskSubmission entity → TaskSubmissionResponse DTO.
     * <p>
     * 📌 @Mapping(target = "taskId", source = "task.id"):
     *    Lấy task.id từ entity và gán vào taskId của DTO.
     *    Vì task là 1 entity, không phải Long field.
     *
     * @param submission Entity từ database
     * @return TaskSubmissionResponse DTO
     */
    @Mapping(target = "taskId", source = "task.id")
    TaskSubmissionResponse toResponse(TaskSubmission submission);
}
