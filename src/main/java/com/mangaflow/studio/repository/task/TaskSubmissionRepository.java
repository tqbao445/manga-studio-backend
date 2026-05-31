package com.mangaflow.studio.repository.task;

import com.mangaflow.studio.model.task.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ── TaskSubmissionRepository ──
 * Repository cho entity TaskSubmission — tầng giao tiếp với database.
 * <p>
 * 📌 extends JpaRepository<TaskSubmission, Long>:
 *    CRUD cơ bản: findAll, findById, save, delete...
 * <p>
 * 📌 Method tự định nghĩa:
 *    findByTaskIdOrderByVersionDesc(): Lấy lịch sử nộp bài của task,
 *    sắp xếp version giảm dần (mới nhất trước).
 */
@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    /**
     * Lấy danh sách submissions của 1 task, sắp xếp theo version giảm dần.
     * <p>
     * 📌 Dùng ở:
     *    - GET  /api/tasks/{taskId}/submissions (endpoint 8)
     *    - POST /api/tasks/{taskId}/submissions — để tính version tiếp theo
     * <p>
     * 📌 SQL tự sinh:
     *    SELECT * FROM task_submissions
     *    WHERE task_id = ?
     *    ORDER BY version DESC
     * <p>
     * 📌 version DESC = version lớn nhất (mới nhất) lên trước.
     *    Dùng .stream().findFirst() để lấy version cao nhất hiện tại.
     *
     * @param taskId ID của task
     * @return List<TaskSubmission> danh sách submissions (mới nhất trước)
     */
    List<TaskSubmission> findByTaskIdOrderByVersionDesc(Long taskId);
}
