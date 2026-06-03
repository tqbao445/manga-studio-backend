package com.mangaflow.studio.repository.task;

import com.mangaflow.studio.model.task.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── TaskRepository ──
 * Repository cho entity Task — tầng giao tiếp với database.
 * <p>
 * 📌 extends JpaRepository<Task, Long>:
 *    Spring Data JPA tự động sinh sẵn CRUD (findAll, findById, save, delete...).
 * <p>
 * 📌 extends JpaSpecificationExecutor<Task>:
 *    Cho phép dùng Specification để xây dựng WHERE clause động
 *    (dùng cho endpoint GET /api/tasks với nhiều filter params).
 *    Các method có sẵn:
 *    - findAll(Specification, Pageable) → Page<Task>
 *    - findAll(Specification)           → List<Task>
 *    - count(Specification)             → long
 * <p>
 * 📌 Các method tự định nghĩa:
 *    findByRegionIdOrderByAssignedAtDesc(): Lấy tasks của 1 region.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Lấy danh sách tasks của 1 region, sắp xếp theo assignedAt giảm dần.
     * <p>
     * 📌 @EntityGraph(attributePaths = "submissions"):
     *    Eagerly load submissions để frontend có thể hiển thị trạng thái ngay sau khi submit.
     * <p>
     * 📌 Dùng ở:
     *    - GET /api/regions/{regionId}/tasks (endpoint 3)
     *
     * @param regionId ID của region
     * @return List<Task> danh sách tasks (mới nhất trước)
     */
    @EntityGraph(attributePaths = "submissions")
    List<Task> findByRegionIdOrderByAssignedAtDesc(Long regionId);
}
