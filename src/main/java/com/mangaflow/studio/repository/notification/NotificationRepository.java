package com.mangaflow.studio.repository.notification;

import com.mangaflow.studio.model.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ── NotificationRepository ──
 * <p>
 * Repository cho entity Notification.
 * Cung cấp các thao tác CRUD cơ bản (thông qua JpaRepository)
 * và các query đặc thù cho tính năng notification.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Các query chính:
 * ══════════════════════════════════════════════════════════════════
 *  1. findAllByUserIdOrderByCreatedAtDesc
 *     → Lấy toàn bộ notification của user, mới nhất lên đầu
 *     → Dùng cho NotificationsPanel hiển thị danh sách
 * <p>
 *  2. countByUserIdAndIsReadFalse
 *     → Đếm số notification chưa đọc
 *     → Dùng cho badge số đỏ ở Topbar
 * <p>
 *  3. findByIdAndUserId
 *     → Tìm notification theo id nhưng kèm kiểm tra chủ sở hữu
 *     → Dùng khi markAsRead để đảm bảo user chỉ đọc được của mình
 * <p>
 *  4. markAllAsReadByUserId (custom @Modifying @Query)
 *     → Update bulk tất cả notification của user thành đã đọc
 *     → Dùng khi user click "Mark all as read"
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Lấy tất cả notification của một user, sắp xếp theo thời gian tạo
     * giảm dần (mới nhất → cũ nhất).
     * <p>
     * Spring Data JPA tự động parse method name để tạo query:
     * - findAllByUserId      : WHERE user_id = ?
     * - OrderByCreatedAtDesc : ORDER BY created_at DESC
     * <p>
     * Dùng List<Notification> thay vì Page vì số lượng notification
     * của mỗi user không quá lớn (hiếm khi > 100).
     *
     * @param userId ID của user cần lấy notification
     * @return Danh sách notification, mới nhất trước
     */
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Đếm số notification chưa đọc của một user.
     * <p>
     * Method name:
     * - countByUserId          : SELECT COUNT(*) WHERE user_id = ?
     * - AndIsReadFalse         : AND is_read = false
     * <p>
     * Kết quả dùng để hiển thị badge số đỏ trên icon chuông ở Topbar.
     *
     * @param userId ID của user cần đếm
     * @return Số lượng notification chưa đọc
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Tìm notification theo id và userId.
     * <p>
     * Dùng Optional để tránh NullPointerException.
     * Nếu không tìm thấy → Optional.empty().
     * <p>
     * Method này kết hợp 2 điều kiện:
     * - WHERE id = ?          (chính xác 1 notification)
     * - AND user_id = ?       (chỉ user sở hữu mới có quyền)
     * <p>
     * Dùng trong markAsRead() để kiểm tra quyền trước khi update.
     *
     * @param id     ID của notification
     * @param userId ID của user (chủ sở hữu)
     * @return Optional<Notification> — empty nếu không tìm thấy hoặc không phải của user
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Đánh dấu tất cả notification của user là đã đọc.
     * <p>
     * ⚠️ @Modifying: Báo Spring Data JPA rằng query này là UPDATE/DELETE,
     * không phải SELECT. Cần có @Transactional ở Service layer.
     * <p>
     * ⚠️ @Query: Viết JPQL (Hibernate Query Language) thay vì SQL thuần.
     * - "UPDATE Notification n" : thay vì "UPDATE notifications"
     * - "n.userId = :userId"    : thay vì "user_id = ?"
     * <p>
     * Lợi ích của JPQL bulk update:
     * - Chỉ 1 câu SQL, không cần load từng entity
     * - Nhanh hơn nhiều so với for-loop + save()
     * <p>
     * Trả về int: số dòng bị ảnh hưởng (có thể dùng để kiểm tra).
     *
     * @param userId ID của user cần mark all as read
     * @return Số notification được update
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    // ════════════════════════════════════════════════════════════
    // CÁC METHOD DÀNH CHO DASHBOARD ACTIVITY FEED
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy 20 notification gần đây nhất (không phân biệt userId).
     * Dùng cho Activity Feed trên Dashboard.
     *
     * @return 20 notification mới nhất
     */
    List<Notification> findTop20ByOrderByCreatedAtDesc();

    /**
     * Lấy 20 notification gần đây nhất của 1 user cụ thể.
     * Dùng cho Activity Feed cá nhân hoá.
     *
     * @param userId ID của user
     * @return 20 notification mới nhất của user đó
     */
    List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
