package com.mangaflow.studio.repository.page;

import com.mangaflow.studio.model.page.Layer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── LayerRepository ──
 * Repository cho entity Layer — thao tác với bảng "layer".
 *
 * 📌 extends JpaRepository<Layer, Long>:
 *    Spring Data JPA tự sinh CRUD cơ bản.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Các query method:
 * ══════════════════════════════════════════════════════════════════
 *  - findByPageIdOrderBySortOrderAsc : Lấy layers của page, sort theo sortOrder
 *  - findMaxSortOrderByPageId       : Tìm sortOrder lớn nhất (dùng khi tạo layer mới)
 *  - countByPageId                   : Đếm số layers của page
 *  - deleteByPageId                  : Xoá toàn bộ layers của page (khi xoá page)
 */
@Repository
public interface LayerRepository extends JpaRepository<Layer, Long> {

    /**
     * Lấy danh sách layers của 1 page, sắp xếp theo sortOrder tăng dần.
     * Dùng ở:
     *   - GET /api/v1/pages/{pageId}/layers
     *   - WorkspaceCanvas render layers theo đúng thứ tự
     *
     * SQL: SELECT * FROM layer WHERE page_id = ? ORDER BY sort_order ASC
     *
     * @param pageId ID của page
     * @return List<Layer> layers đã sắp xếp
     */
    List<Layer> findByPageIdOrderBySortOrderAsc(Long pageId);

    /**
     * Tìm sortOrder lớn nhất trong các layers của 1 page.
     * Dùng khi tạo layer mới từ APPROVED submission:
     *   sortOrder = maxSortOrder + 1 → layer mới luôn ở trên cùng.
     *
     * Nếu chưa có layer nào → trả về Optional.empty().
     *
     * @param pageId ID của page
     * @return Optional<Integer> sortOrder lớn nhất (hoặc empty nếu chưa có layer)
     */
    @Query("SELECT COALESCE(MAX(l.sortOrder), -1) FROM Layer l WHERE l.pageId = :pageId")
    int findMaxSortOrderByPageId(@Param("pageId") Long pageId);

    /**
     * Đếm số layers của 1 page.
     * Dùng kiểm tra trước khi xoá page.
     *
     * @param pageId ID của page
     * @return số lượng layers
     */
    long countByPageId(Long pageId);

    /**
     * Xoá toàn bộ layers của 1 page.
     * Dùng khi xoá page — cascade từ DB.
     *
     * @param pageId ID của page
     */
    void deleteByPageId(Long pageId);
}
