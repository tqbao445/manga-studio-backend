package com.mangaflow.studio.repository.series;

import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.SeriesAssistant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── SeriesAssistantRepository ──
 * Repository cho entity SeriesAssistant — tầng giao tiếp với bảng "series_assistant".
 * <p>
 * 📌 extends JpaRepository<SeriesAssistant, Long>:
 *    Cung cấp sẵn CRUD + phân trang.
 * <p>
 * 📌 Các method query dùng trong:
 *    - SeriesAssistantService (invite, respond, list, remove)
 *    - SeriesSpecification (subquery filter cho ASSISTANT role)
 *    - TaskService (kiểm tra assistant có trong series không)
 */
@Repository
public interface SeriesAssistantRepository extends JpaRepository<SeriesAssistant, Long> {

    /**
     * Tìm tất cả record của 1 series theo trạng thái.
     * <p>
     * Dùng trong:
     *   - getSeriesAssistants(seriesId) → lấy danh sách ACCEPTED
     *   - Kiểm tra series có bao nhiêu lời mời đang chờ
     *
     * @param seriesId ID của series
     * @param status   trạng thái cần lọc (PENDING / ACCEPTED / REJECTED)
     * @return danh sách SeriesAssistant (có thể rỗng)
     */
    List<SeriesAssistant> findBySeriesIdAndStatus(Long seriesId, InvitationStatus status);

    /**
     * Tìm tất cả lời mời của 1 assistant theo trạng thái.
     * <p>
     * Dùng trong:
     *   - getPendingInvitations() → lấy danh sách PENDING của assistant hiện tại
     *   - Kiểm tra assistant đã ACCEPTED ở những series nào
     *
     * @param assistantId ID của assistant
     * @param status      trạng thái cần lọc
     * @return danh sách lời mời (có thể rỗng)
     */
    List<SeriesAssistant> findByAssistantIdAndStatus(Long assistantId, InvitationStatus status);

    /**
     * Tìm lời mời theo cặp series + assistant.
     * <p>
     * Dùng trong:
     *   - inviteAssistant(): kiểm tra đã có lời mời trước đó chưa
     *   - respondToInvitation(): lấy record để cập nhật status
     * <p>
     * 📌 Nhờ UNIQUE(series_id, assistant_id) nên chỉ trả về 0 hoặc 1 kết quả.
     *
     * @param seriesId    ID của series
     * @param assistantId ID của assistant
     * @return Optional<SeriesAssistant> — empty nếu chưa có lời mời nào
     */
    Optional<SeriesAssistant> findBySeriesIdAndAssistantId(Long seriesId, Long assistantId);

    /**
     * Kiểm tra assistant có tồn tại trong series với trạng thái cụ thể không.
     * <p>
     * Dùng trong:
     *   - TaskService.createTask(): kiểm tra assistant có ACCEPTED không
     *     trước khi cho phép gán task
     *   - inviteAssistant(): kiểm tra đã ACCEPTED chưa để tránh mời lại
     *
     * @param seriesId    ID của series
     * @param assistantId ID của assistant
     * @param status      trạng thái cần kiểm tra
     * @return true nếu có record với trạng thái đó
     */
    boolean existsBySeriesIdAndAssistantIdAndStatus(
            Long seriesId, Long assistantId, InvitationStatus status);

    /**
     * Xoá tất cả record của 1 cặp series + assistant.
     * <p>
     * Dùng trong:
     *   - removeAssistant(): mangaka xoá assistant khỏi series
     * <p>
     * 📌 Vì có UNIQUE constraint nên chỉ xoá tối đa 1 record.
     *
     * @param seriesId    ID của series
     * @param assistantId ID của assistant
     */
    void deleteBySeriesIdAndAssistantId(Long seriesId, Long assistantId);
}
