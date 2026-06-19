package com.mangaflow.studio.repository.region;

import com.mangaflow.studio.model.region.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── RegionRepository ──
 * Repository cho entity Region — tầng giao tiếp với database.
 * <p>
 * 📌 extends JpaRepository<Region, Long>:
 *    Spring Data JPA tự động sinh sẵn các method CRUD cơ bản:
 * <p>
 *    ┌────────────────┬────────────────────────────────────┐
 *    │ Method         │ Mục đích                            │
 *    ├────────────────┼────────────────────────────────────┤
 *    │ findAll()      │ Lấy tất cả regions                  │
 *    │ findById(id)   │ Tìm region theo ID                  │
 *    │ save(entity)   │ Tạo mới hoặc cập nhật region        │
 *    │ delete(entity) │ Xoá region                          │
 *    │ count()        │ Đếm tổng số regions                 │
 *    └────────────────┴────────────────────────────────────┘
 * <p>
 * 📌 Các method tự định nghĩa bên dưới:
 *    Spring Data JPA tự động sinh câu SQL từ tên method.
 *    VD: findByPageIdOrderBySortOrderAsc
 *      → SELECT * FROM region WHERE page_id = ? ORDER BY sort_order ASC
 */
@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    /**
     * Lấy danh sách regions của 1 page, sắp xếp theo sort_order tăng dần.
     * <p>
     * 📌 Dùng ở:
     *    - GET /api/v1/pages/{pageId}/regions → list regions trên canvas
     * <p>
     * 📌 SQL tự sinh:
     *    SELECT * FROM region
     *    WHERE page_id = ?
     *    ORDER BY sort_order ASC
     * <p>
     * 📌 sort_order ASC = region ở dưới cùng (background) lên trước,
     *    region ở trên cùng (character, text) sau.
     *    Khớp với thứ tự render trên canvas.
     *
     * @param pageId ID của page cần lấy regions
     * @return List<Region> danh sách regions đã sắp xếp
     */
    List<Region> findByPageIdOrderBySortOrderAsc(Long pageId);

    /**
     * Lấy danh sách regions theo danh sách pageIds.
     * Dùng cho filter seriesId trong TaskService.
     * <p>
     * 📌 SQL tự sinh:
     *    SELECT * FROM region WHERE page_id IN (?)
     *
     * @param pageIds Danh sách IDs của pages
     * @return List<Region> danh sách regions
     */
    List<Region> findByPageIdIn(List<Long> pageIds);

    /**
     * Lấy danh sách regions thuộc 1 task.
     * Dùng khi cần lấy regions của task để hiển thị.
     *
     * @param taskId ID của task
     * @return List<Region> danh sách regions
     */
    List<Region> findByTaskId(Long taskId);
}
