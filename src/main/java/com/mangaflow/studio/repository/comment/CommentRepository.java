package com.mangaflow.studio.repository.comment;

import com.mangaflow.studio.model.comment.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── CommentRepository ──
 * Repository cho entity Comment — tầng giao tiếp với database.
 * Là cầu nối giữa Service và Database, giúp Service không phải
 * viết câu lệnh SQL thủ công.
 *
 * 📌 extends JpaRepository<Comment, Long>:
 *    Spring Data JPA tự động sinh sẵn các method CRUD cơ bản:
 *
 *    ┌─────────────────────┬──────────────────────────────────────┐
 *    │ Method              │ Mục đích                              │
 *    ├─────────────────────┼──────────────────────────────────────┤
 *    │ findAll()           │ Lấy tất cả comments                   │
 *    │ findById(id)        │ Tìm comment theo ID                  │
 *    │ save(entity)        │ Tạo mới hoặc cập nhật comment        │
 *    │ delete(entity)      │ Xoá comment                          │
 *    │ count()             │ Đếm tổng số comments                 │
 *    │ deleteAll(entities) │ Xoá nhiều comments cùng lúc          │
 *    └─────────────────────┴──────────────────────────────────────┘
 *
 * 📌 Các method tuỳ chỉnh (viết thêm) bên dưới:
 *    Spring Data JPA tự động sinh câu SQL từ tên method.
 *
 *    Quy tắc đặt tên method:
 *    - findBy... : SELECT WHERE
 *    - OrderBy... : ORDER BY
 *    - Asc/Desc : ASC/DESC
 *    - And/Or : AND/OR
 *    - find...AndParentIsNull : WHERE parent_id IS NULL
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Lấy tất cả comment GỐC (parent = null) của 1 page.
     *
     * 📌 Dùng ở:
     *    - GET /api/v1/pages/{pageId}/comments — danh sách annotations
     *
     * 📌 SQL tự sinh:
     *    SELECT * FROM comments
     *    WHERE page_id = ? AND parent_id IS NULL
     *    ORDER BY created_at ASC
     *
     * 📌 parent_id IS NULL = chỉ lấy comment gốc, không lấy reply.
     * 📌 created_at ASC = comment cũ lên trước, mới xuống sau.
     *
     * @param pageId ID của page cần lấy comments
     * @return List<Comment> danh sách comment gốc, theo thứ tự thời gian
     */
    List<Comment> findByPageIdAndParentIsNullOrderByCreatedAtAsc(Long pageId);

    /**
     * Lấy tất cả replies của 1 comment (dựa vào parentId).
     *
     * 📌 Dùng ở:
     *    - Service.getByPage() — populate replies cho từng comment gốc
     *    - Service.getById() — populate replies cho 1 comment
     *
     * 📌 SQL tự sinh:
     *    SELECT * FROM comments
     *    WHERE parent_id = ?
     *    ORDER BY created_at ASC
     *
     * 📌 created_at ASC = reply nào viết trước hiện trước.
     *
     * @param parentId ID của comment cha
     * @return List<Comment> danh sách replies, theo thứ tự thời gian
     */
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    /**
     * Tìm comment theo id và authorId.
     *
     * 📌 Dùng ở:
     *    - Service.update() — kiểm tra "comment này có phải của tôi không?"
     *    - Service.delete() — kiểm tra quyền xoá
     *
     * 📌 SQL tự sinh:
     *    SELECT * FROM comments WHERE id = ? AND author_id = ?
     *
     * 📌 Trả về Optional:
     *    - Nếu tìm thấy → có quyền sửa/xoá
     *    - Nếu không → throw 404 hoặc 403
     *
     * @param id       ID của comment
     * @param authorId ID của người viết
     * @return Optional<Comment> có thể rỗng nếu không tìm thấy hoặc không phải chủ
     */
    Optional<Comment> findByIdAndAuthorId(Long id, Long authorId);

    /**
     * Lấy TẤT CẢ comments của 1 page (gồm cả gốc và reply).
     *
     * 📌 Dùng ở:
     *    - Service.delete() — lấy tất cả replies của 1 comment để xoá
     *      (trước khi xoá comment gốc)
     *
     * 📌 SQL tự sinh:
     *    SELECT * FROM comments WHERE page_id = ?
     *
     * @param pageId ID của page
     * @return List<Comment> tất cả comments của page
     */
    List<Comment> findByPageId(Long pageId);

    /**
     * Đếm số lượng replies của 1 comment.
     *
     * 📌 Dùng ở:
     *    - Service.delete() — kiểm tra có replies không trước khi xoá
     *
     * 📌 SQL tự sinh:
     *    SELECT COUNT(*) FROM comments WHERE parent_id = ?
     *
     * @param parentId ID của comment cha
     * @return số lượng replies
     */
    long countByParentId(Long parentId);
}
