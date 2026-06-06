package com.mangaflow.studio.repository.series;

import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.SeriesTantouInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── SeriesTantouInvitationRepository ──
 * Repository cho entity SeriesTantouInvitation.
 * <p>
 * 📌 extends JpaRepository<SeriesTantouInvitation, Long>:
 *    Cung cấp sẵn CRUD + phân trang.
 * <p>
 * 📌 Các method query dùng trong:
 *    - SeriesTantouInvitationService (invite, respond, list, remove)
 */
@Repository
public interface SeriesTantouInvitationRepository
        extends JpaRepository<SeriesTantouInvitation, Long> {

    /**
     * Tìm lời mời theo cặp series + tantou.
     * <p>
     * Dùng trong:
     *   - invite(): kiểm tra đã có lời mời trước đó chưa
     *   - remove(): lấy record để xoá
     * <p>
     * Nhờ UNIQUE(series_id, tantou_id) nên chỉ trả về 0 hoặc 1 kết quả.
     *
     * @param seriesId ID của series
     * @param tantouId ID của tantou
     * @return Optional<SeriesTantouInvitation>
     */
    Optional<SeriesTantouInvitation> findBySeriesIdAndTantouId(
            Long seriesId, Long tantouId);

    /**
     * Tìm tất cả lời mời của 1 tantou theo trạng thái.
     * <p>
     * Dùng trong:
     *   - getPendingInvitations(): tantou xem danh sách lời mời đang chờ
     *
     * @param tantouId ID của tantou
     * @param status   trạng thái cần lọc
     * @return danh sách lời mời
     */
    List<SeriesTantouInvitation> findByTantouIdAndStatus(
            Long tantouId, InvitationStatus status);

    /**
     * Tìm tất cả lời mời của 1 series theo trạng thái.
     * <p>
     * Dùng trong:
     *   - getSeriesTantouInvitations(): mangaka xem danh sách lời mời
     *
     * @param seriesId ID của series
     * @param status   trạng thái cần lọc
     * @return danh sách lời mời
     */
    List<SeriesTantouInvitation> findBySeriesIdAndStatus(
            Long seriesId, InvitationStatus status);

    /**
     * Kiểm tra đã có lời mời với trạng thái cụ thể chưa.
     * <p>
     * Dùng trong:
     *   - invite(): kiểm tra đã PENDING hoặc ACCEPTED để throw lỗi
     *
     * @param seriesId ID của series
     * @param tantouId ID của tantou
     * @param status   trạng thái cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsBySeriesIdAndTantouIdAndStatus(
            Long seriesId, Long tantouId, InvitationStatus status);

    /**
     * Xoá lời mời theo cặp series + tantou.
     * <p>
     * Dùng trong:
     *   - removeTantouInvitation(): mangaka xoá/huỷ lời mời
     * <p>
     * Nhờ UNIQUE constraint nên chỉ xoá tối đa 1 record.
     *
     * @param seriesId ID của series
     * @param tantouId ID của tantou
     */
    void deleteBySeriesIdAndTantouId(Long seriesId, Long tantouId);
}
