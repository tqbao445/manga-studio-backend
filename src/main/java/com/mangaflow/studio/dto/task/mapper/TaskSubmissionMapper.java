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
 *  → @Mapping để lấy id từ task entity
 * <p>
 *  thumbnailUrl: không có trong entity → tạo từ resultImageUrl
 *  → expression = "java(generateThumbnailUrl(submission.getResultImageUrl()))"
 *  → chèn resize w_320 vào Cloudinary URL
 */
@Mapper(componentModel = "spring")
public interface TaskSubmissionMapper {

    /**
     * Chuyển đổi TaskSubmission entity → TaskSubmissionResponse DTO.
     * <p>
     * 📌 thumbnailUrl được sinh tự động từ resultImageUrl
     *    (chèn c_limit,w_320 vào Cloudinary URL), không lưu trong DB.
     *
     * @param submission Entity từ database
     * @return TaskSubmissionResponse DTO
     */
    @Mapping(target = "taskId", source = "task.id")
    @Mapping(target = "thumbnailUrl",
            expression = "java(generateThumbnailUrl(submission.getResultImageUrl()))")
    TaskSubmissionResponse toResponse(TaskSubmission submission);

    /**
     * Tạo URL thumbnail từ Cloudinary URL gốc.
     * <p>
     * Chèn transformation "c_limit,w_320" để Cloudinary resize ảnh
     * xuống width 320px — load nhanh trong danh sách submissions.
     * <p>
     * URL gốc:   https://res.cloudinary.com/.../image/upload/v{version}/{publicId}.{ext}
     * URL thumb: https://res.cloudinary.com/.../image/upload/c_limit,w_320/v{version}/{publicId}.{ext}
     *
     * @param imageUrl URL Cloudinary gốc (có thể null)
     * @return URL đã resize, hoặc null nếu input null
     */
    default String generateThumbnailUrl(String imageUrl) {
        if (imageUrl == null) return null;
        return imageUrl.replace("/upload/", "/upload/c_limit,w_320/");
    }
}
