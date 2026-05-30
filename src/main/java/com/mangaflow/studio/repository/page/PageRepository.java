package com.mangaflow.studio.repository.page;

import com.mangaflow.studio.model.page.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── PageRepository ──
 * Repository cho entity Page — tầng giao tiếp với database.
 * Là cầu nối giữa Service và Database, giúp Service không phải
 * viết câu lệnh SQL thủ công.
 *
 * 📌 extends JpaRepository<Page, Long>:
 *    Spring Data JPA tự động sinh sẵn các method CRUD cơ bản:
 *
 *    ┌─────────────────────┬──────────────────────────────────────┐
 *    │ Method              │ Mục đích                              │
 *    ├─────────────────────┼──────────────────────────────────────┤
 *    │ findAll()           │ Lấy tất cả pages                      │
 *    │ findById(id)        │ Tìm page theo ID                      │
 *    │ save(entity)        │ Tạo mới hoặc cập nhật page            │
 *    │ delete(entity)      │ Xoá page                              │
 *    │ count()             │ Đếm tổng số pages                     │
 *    └─────────────────────┴──────────────────────────────────────┘
 *
 * 📌 Các method自定义 (viết thêm) bên dưới:
 *    Spring Data JPA tự động sinh câu SQL từ tên method.
 *    VD: findByChapterIdOrderByPageNumberAsc
 *      → SELECT * FROM pages WHERE chapter_id = ? ORDER BY page_number ASC
 *
 *    Quy tắc đặt tên method:
 *    - findBy... : SELECT WHERE
 *    - OrderBy... : ORDER BY
 *    - Asc/Desc : ASC/DESC
 *    - And/Or : AND/OR
 *    - countBy... : SELECT COUNT(*)
 *    - existsBy... : SELECT EXISTS(SELECT 1 ...)
 */
@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    /**
     * Lấy danh sách pages của 1 chapter, sắp xếp theo số thứ tự tăng dần.
     *
     * 📌 Dùng ở:
     *    - GET /api/v1/chapters/{chapterId}/pages -> list pages
     *
     * 📌 SQL tự sinh:
     *    SELECT * FROM pages
     *    WHERE chapter_id = ?
     *    ORDER BY page_number ASC
     *
     * 📌 page_number ASC = page 1 lên trước, page 2 sau, ...
     *    Khớp với thứ tự đọc truyện từ trái sang phải, từ trên xuống dưới.
     *
     * @param chapterId ID của chapter cần lấy pages
     * @return List<Page> danh sách pages đã sắp xếp
     */
    List<Page> findByChapterIdOrderByPageNumberAsc(Long chapterId);

    /**
     * Đếm số lượng pages trong 1 chapter.
     *
     * 📌 Dùng ở:
     *    - Kiểm tra xem chapter có pages không (trước khi xoá chapter)
     *    - Hiển thị số lượng pages trong response
     *
     * 📌 SQL tự sinh:
     *    SELECT COUNT(*) FROM pages WHERE chapter_id = ?
     *
     * @param chapterId ID của chapter
     * @return số lượng pages
     */
    long countByChapterId(Long chapterId);

    /**
     * Kiểm tra xem 1 chapter đã có page với số thứ tự này chưa.
     *
     * 📌 Dùng ở:
     *    - Khi upload page mới -> kiểm tra trùng pageNumber
     *    - Khi reorder -> kiểm tra pageNumber đã tồn tại chưa
     *
     * 📌 SQL tự sinh:
     *    SELECT EXISTS(SELECT 1 FROM pages
     *                  WHERE chapter_id = ? AND page_number = ?)
     *
     * @param chapterId  ID của chapter
     * @param pageNumber Số thứ tự page cần kiểm tra
     * @return true nếu đã tồn tại, false nếu chưa
     */
    boolean existsByChapterIdAndPageNumber(Long chapterId, Integer pageNumber);
}
