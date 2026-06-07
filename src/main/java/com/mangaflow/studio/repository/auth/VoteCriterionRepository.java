package com.mangaflow.studio.repository.auth;

import com.mangaflow.studio.model.auth.VoteCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── VoteCriterionRepository ──
 * Repository cho entity VoteCriterion.
 *
 * Tại sao cần các method này?
 * - findByIsActiveTrueOrderBySortOrderAsc: Lấy danh sách tiêu chí đang dùng,
 *   sắp xếp theo thứ tự hiển thị. Dùng khi load form vote.
 */
@Repository
public interface VoteCriterionRepository extends JpaRepository<VoteCriterion, Long> {

    List<VoteCriterion> findByIsActiveTrueOrderBySortOrderAsc();
}
