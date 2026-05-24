package com.mangaflow.studio.series.repository;

import com.mangaflow.studio.series.model.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ── SeriesRepository ──
 * Repository cho entity Series — tầng giao tiếp với database.
 *
 * 📌 extends JpaRepository<Series, Long>:
 *    Cung cấp sẵn các method CRUD: findAll(), findById(), save(), delete(),...
 *
 * 📌 extends JpaSpecificationExecutor<Series>:
 *    Cho phép build query động bằng Specification (xem SeriesService.getAll()).
 *    Thay thế cho việc phải viết nhiều method findByXxx trong interface.
 *
 * 📌 Chỉ giữ lại 2 method đặc thù:
 *    - findByIdAndMangakaId: dùng để kiểm tra ownership
 *    - countByMangakaId: đếm số series của 1 mangaka
 *
 * 📌 Các query findByStatus, findByTitleContaining...:
 *    Không cần khai báo ở đây — dùng Specification trong Service.
 */
@Repository
public interface SeriesRepository extends JpaRepository<Series, Long>,
                                          JpaSpecificationExecutor<Series> {

    /**
     * Tìm series theo id + mangakaId.
     * Dùng để kiểm tra ownership trước khi update/delete.
     *
     * Nếu không tìm thấy → series không tồn tại hoặc không phải của user này
     * → throw 403 Forbidden.
     *
     * @param id id của series
     * @param mangakaId id của mangaka (lấy từ token)
     * @return Optional<Series> — empty nếu không phải chủ sở hữu
     */
    Optional<Series> findByIdAndMangakaId(Long id, Long mangakaId);

    /**
     * Đếm số series của một mangaka.
     *
     * @param mangakaId id của mangaka
     * @return số lượng series
     */
    long countByMangakaId(Long mangakaId);
}
