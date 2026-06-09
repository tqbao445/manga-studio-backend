package com.mangaflow.studio.dto.task.mapper;

import com.mangaflow.studio.dto.task.response.TaskResponse;
import com.mangaflow.studio.dto.task.response.UserBasicDTO;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.task.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ── TaskMapper ──
 * MapStruct mapper — chuyển đổi giữa Entity Task và TaskResponse DTO.
 * <p>
 * 📌 @Mapper(componentModel = "spring"):
 *    MapStruct tự động sinh class implementation (TaskMapperImpl) lúc compile.
 *    componentModel = "spring" → implementation được đánh @Component,
 *    tự động inject được bằng @Autowired / constructor injection.
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Các mapping đặc biệt:
 * ════════════════════════════════════════════════════════════════
 * <p>
 *  1. regionId: Task.region.id → TaskResponse.regionId
 *     → Dùng @Mapping để MapStruct biết lấy id từ region
 * <p>
 *  2. assistant / assignedBy: User entity → UserBasicDTO
 *     → Dùng default method toBasicUser() để map thủ công
 *     → Chỉ lấy id, displayName, avatarUrl (không chứa email, password...)
 * <p>
 *  3. submissions / attachments: List entity → List response DTO
 *     → MapStruct tự động map List<TaskSubmission> → List<TaskSubmissionResponse>
 *     → Vì TaskSubmissionMapper đã được khai báo trong uses
 * <p>
 * ════════════════════════════════════════════════════════════════
 *  Các field tự động map (cùng tên):
 * ════════════════════════════════════════════════════════════════
 *  Task.id         → TaskResponse.id
 *  Task.title      → TaskResponse.title
 *  Task.status     → TaskResponse.status
 *  Task.priority   → TaskResponse.priority
 *  Task.createdAt  → TaskResponse.createdAt
 *  ...và các field cùng tên khác
 */
@Mapper(componentModel = "spring", uses = {TaskSubmissionMapper.class, TaskAttachmentMapper.class})
public interface TaskMapper {

    /**
     * Chuyển đổi Task entity → TaskResponse DTO.
     * <p>
     * 📌 @Mapping(target = "regionId", source = "region.id"):
     *    Lấy region.id từ entity và gán vào regionId của DTO.
     *    Vì Region là 1 entity riêng, không phải Long field.
     * <p>
     * 📌 expression = "java(toBasicUser(task.getAssistant()))":
     *    Gọi method default để map User entity → UserBasicDTO.
     *    Chỉ lấy id, displayName, avatarUrl.
     * <p>
     * 📌 submissions và attachments:
     *    MapStruct tự động map nhờ uses = {TaskSubmissionMapper.class, TaskAttachmentMapper.class}.
     *    Nếu list null → trả về null.
     *
     * @param task Entity từ database
     * @return TaskResponse DTO gửi về frontend
     */
    @Mapping(target = "regionId", source = "region.id")
    @Mapping(target = "regionX", expression = "java(task.getRegion() != null ? task.getRegion().getX() : null)")
    @Mapping(target = "regionY", expression = "java(task.getRegion() != null ? task.getRegion().getY() : null)")
    @Mapping(target = "regionWidth", expression = "java(task.getRegion() != null ? task.getRegion().getWidth() : null)")
    @Mapping(target = "regionHeight", expression = "java(task.getRegion() != null ? task.getRegion().getHeight() : null)")
    @Mapping(target = "regionLabel", expression = "java(task.getRegion() != null ? task.getRegion().getLabel() : null)")
    @Mapping(target = "assistant", expression = "java(toBasicUser(task.getAssistant()))")
    @Mapping(target = "assignedBy", expression = "java(toBasicUser(task.getAssignedBy()))")
    TaskResponse toResponse(Task task);

    /**
     * Method default — chuyển User entity → UserBasicDTO.
     * <p>
     * 📌 Đây là default method trong interface (Java 8+).
     *    MapStruct sẽ giữ nguyên method này trong implementation.
     * <p>
     * 📌 Chỉ lấy 3 field: id, displayName, avatarUrl.
     *    Không chứa thông tin nhạy cảm (email, password, role...).
     * <p>
     * 📌 Null safety: nếu user = null → trả về null.
     *
     * @param user User entity (có thể null)
     * @return UserBasicDTO hoặc null
     */
    default UserBasicDTO toBasicUser(User user) {
        if (user == null) return null;
        return UserBasicDTO.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
