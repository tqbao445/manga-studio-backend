package com.mangaflow.studio.repository.task;

import com.mangaflow.studio.model.task.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ── TaskAttachmentRepository ──
 * Repository cho entity TaskAttachment — tầng giao tiếp với database.
 * <p>
 * 📌 extends JpaRepository<TaskAttachment, Long>:
 *    CRUD cơ bản: findAll, findById, save, delete...
 * <p>
 * 📌 Không cần method custom vì:
 *    - Thêm attachment: dùng Task.getAttachments().add() (cascade)
 *    - Xoá attachment: dùng findById + delete
 *    - Lấy danh sách: thông qua Task.getAttachments() (đã load từ task detail)
 */
@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
}
