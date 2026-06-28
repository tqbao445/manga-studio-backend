package com.mangaflow.studio.dto.task.mapper;

import com.mangaflow.studio.dto.task.response.RegionBasicDTO;
import com.mangaflow.studio.dto.task.response.TaskResponse;
import com.mangaflow.studio.dto.task.response.UserBasicDTO;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.region.Region;
import com.mangaflow.studio.model.task.Task;
import com.mangaflow.studio.model.task.TaskStatus;
import com.mangaflow.studio.model.task.TaskSubmission;
import com.mangaflow.studio.model.task.TaskSubmissionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * ── TaskMapper ──
 * MapStruct mapper: chuyển đổi giữa Task entity ↔ TaskResponse DTO.
 * <p>
 * 📌 componentModel = "spring":
 *    Spring tự động tạo Bean (TaskMapper IMPL) — inject được ở Service.
 * <p>
 * 📌 uses = {TaskSubmissionMapper.class, TaskAttachmentMapper.class}:
 *    Khi map submissions / attachments bên trong TaskResponse,
 *    MapStruct tự động gọi các mapper này.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Các mapping đặc biệt (expression):
 * ══════════════════════════════════════════════════════════════════
 *  - regions → toRegionBasicList(): Set<Region> → List<RegionBasicDTO>
 *    Vì Region entity có nhiều field hơn DTO (x, y, w, h, color...)
 *    cần chuyển thủ công.
 *  - assistant / assignedBy → toBasicUser(): User → UserBasicDTO
 *    Chỉ lấy id + displayName + avatarUrl, bỏ password, email, ...
 *  - revisionNote → getLatestRevisionNote(): Lấy reviewNote từ
 *    submission REVISION_REQUIRED gần nhất.
 *    Đây là logic đặc thù — MapStruct không làm được tự động
 *    → dùng default method.
 */
@Mapper(componentModel = "spring", uses = {TaskSubmissionMapper.class, TaskAttachmentMapper.class})
public interface TaskMapper {

    /**
     * toResponse: Task → TaskResponse.
     * <p>
     * Các field tự động map (cùng tên):
     *   id, title, regionType, description, notes, referenceImageUrl,
     *   pageImageUrl, status, priority, assignedAt, dueDate, createdAt
     * <p>
     * Các field map thủ công (expression):
     *   regions:     Set<Region> → List<RegionBasicDTO>
     *   assistant:   User → UserBasicDTO
     *   assignedBy:  User → UserBasicDTO
     *   revisionNote: Từ submission gần nhất
     */
    @Mapping(target = "regions", expression = "java(toRegionBasicList(task.getRegions()))")
    @Mapping(target = "assistant", expression = "java(toBasicUser(task.getAssistant()))")
    @Mapping(target = "assignedBy", expression = "java(toBasicUser(task.getAssignedBy()))")
    @Mapping(target = "revisionNote", expression = "java(getLatestRevisionNote(task))")
    TaskResponse toResponse(Task task);

    default List<RegionBasicDTO> toRegionBasicList(Set<Region> regions) {
        if (regions == null) return Collections.emptyList();
        return regions.stream()
                .map(this::toBasicRegion)
                .toList();
    }

    default RegionBasicDTO toBasicRegion(Region region) {
        if (region == null) return null;
        return RegionBasicDTO.builder()
                .id(region.getId())
                .regionType(region.getRegionType())
                .label(region.getLabel())
                .x(region.getX())
                .y(region.getY())
                .width(region.getWidth())
                .height(region.getHeight())
                .color(region.getColor())
                .build();
    }

    default UserBasicDTO toBasicUser(User user) {
        if (user == null) return null;
        return UserBasicDTO.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // getLatestRevisionNote — Lấy revisionNote từ submission gần nhất
    // ════════════════════════════════════════════════════════════════
    //
    // Mục đích:
    //   Khi task ở trạng thái REVISE, ASSISTANT cần biết MANGAKA yêu
    //   cầu sửa gì. Note này nằm ở submission gần nhất (version lớn
    //   nhất) có status = REVISION_REQUIRED.
    //
    // Cách hoạt động:
    //   1. Kiểm tra task.status == REVISE → nếu không → null
    //   2. Lọc submissions có status == REVISION_REQUIRED
    //   3. Tìm submission có version lớn nhất trong số đó
    //   4. Trả về reviewNote của submission đó
    //
    // 📌 submissions là LAZY — Khi gọi getSubmissions() lần đầu,
    //    Hibernate tự động query. Vì mapper chạy trong @Transactional
    //    ở Service, LAZY load được.
    //
    // 📌 performance: Nếu danh sách submissions dài (hiếm — thường
    //    chỉ 1-3 bản), việc lọc + sort trong memory là chấp nhận được.
    //    Nếu cần tối ưu, có thể dùng @Query ở Repository.
    //
    // @param task Task entity (có thể đã load submissions LAZY)
    // @return revisionNote hoặc null nếu không có
    default String getLatestRevisionNote(Task task) {
        // ── Chỉ lấy khi task REVISE ──
        if (task.getStatus() != TaskStatus.REVISE) {
            return null;
        }

        // ── Lọc submissions REVISION_REQUIRED ──
        List<TaskSubmission> revisionSubmissions = task.getSubmissions().stream()
                .filter(s -> s.getStatus() == TaskSubmissionStatus.REVISION_REQUIRED)
                .toList();

        if (revisionSubmissions.isEmpty()) return null;

        // ── Tìm submission có version lớn nhất ──
        TaskSubmission latest = revisionSubmissions.stream()
                .max(Comparator.comparing(TaskSubmission::getVersion))
                .orElse(null);

        return latest != null ? latest.getReviewNote() : null;
    }
}
