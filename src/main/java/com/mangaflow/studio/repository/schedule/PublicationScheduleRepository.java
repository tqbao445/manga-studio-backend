package com.mangaflow.studio.repository.schedule;

import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.schedule.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── PublicationScheduleRepository ──
 * Repository cho entity PublicationSchedule — tầng giao tiếp với database.
 *
 * 📌 extends JpaRepository<PublicationSchedule, Long>:
 *    Cung cấp sẵn các method CRUD: findAll(), findById(), save(), delete(),...
 *
 * 📌 extends JpaSpecificationExecutor<PublicationSchedule>:
 *    Cho phép build WHERE clause động bằng Specification (xem Service).
 *    Các method có sẵn:
 *      - findAll(Specification, Pageable) → Page<PublicationSchedule> (dùng cho phân trang)
 *      - findAll(Specification)           → List<PublicationSchedule>
 *      - count(Specification)             → long
 *
 * 📌 Các method query:
 *    - findBySeriesId:             Lấy tất cả schedules của 1 series (dùng cho lịch sử)
 *    - findBySeriesIdAndStatus:    Lấy schedule ACTIVE của series (dùng khi tạo mới / cron job)
 *    - findByStatus:               Lấy tất cả schedules theo trạng thái (dùng cho cron job quét ACTIVE)
 */
@Repository
public interface PublicationScheduleRepository
        extends JpaRepository<PublicationSchedule, Long>,
                JpaSpecificationExecutor<PublicationSchedule> {

    /**
     * Lấy tất cả schedules của một series (gồm ACTIVE, PAUSED, COMPLETED).
     * Dùng để hiển thị lịch sử schedule trên UI.
     *
     * @param seriesId ID của series
     * @return List<PublicationSchedule> danh sách schedules
     */
    List<PublicationSchedule> findBySeriesId(Long seriesId);

    /**
     * Lấy schedule của series theo trạng thái cụ thể.
     * Dùng để:
     *   - Kiểm tra series đã có schedule ACTIVE chưa (khi tạo mới)
     *   - Cron job lấy schedule ACTIVE để xử lý
     *
     * @param seriesId ID của series
     * @param status   trạng thái cần tìm (ACTIVE / PAUSED / COMPLETED)
     * @return Optional<PublicationSchedule>
     */
    Optional<PublicationSchedule> findBySeriesIdAndStatus(Long seriesId, ScheduleStatus status);

    /**
     * Lấy tất cả schedules theo trạng thái.
     * Dùng cho cron job: tìm tất cả schedule ACTIVE để quét và xử lý.
     *
     * @param status trạng thái cần tìm
     * @return List<PublicationSchedule> danh sách schedules
     */
    List<PublicationSchedule> findByStatus(ScheduleStatus status);
}
