package com.mangaflow.studio.model.page;

import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.page.Page;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── Layer Entity ──
 * Ánh xạ tới bảng "layer" trong database.
 * Mỗi Layer là 1 lớp đồ hoạ chồng lên Page (giống layer trong Photoshop).
 * Khi MANGAKA approve 1 TaskSubmission → tạo Layer mới với fileUrl = resultImageUrl.
 *
 * 📌 @Entity: JPA entity → Hibernate tự động quản lý bảng "layer".
 * 📌 @Table: name = "layer"
 *
 * ══════════════════════════════════════════════════════════════════
 *  Quan hệ:
 * ══════════════════════════════════════════════════════════════════
 *  - pageId: Long (FK → pages.id) — dùng cho INSERT/UPDATE
 *  - page: @ManyToOne(LAZY) — chỉ đọc (insertable/updatable = false)
 *    Giống pattern Page→Chapter: code cũ set pageId vẫn hoạt động.
 *  - createdBy: @ManyToOne(LAZY) → User (người tạo layer)
 *
 * ══════════════════════════════════════════════════════════════════
 *  Luồng tạo Layer:
 * ══════════════════════════════════════════════════════════════════
 *  1. ASSISTANT submit task → TaskSubmission.resultImageUrl (upload Cloudinary)
 *  2. MANGAKA review → APPROVED
 *  3. TaskService.reviewSubmission() gọi LayerService.createLayer()
 *  4. LayerService tạo entity với:
 *       - pageId = task.region.pageId
 *       - fileUrl = submission.resultImageUrl
 *       - label = task.title
 *       - sortOrder = maxSortOrder(pageId) + 1
 *       - createdBy = submission.assistant
 *       (page là @ManyToOne read-only, không cần set)
 */
@Entity
@Table(name = "layer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Layer {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     * SQL Server IDENTITY = MySQL AUTO_INCREMENT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * pageId: FK đến bảng pages (BIGINT, NOT NULL).
     * Dùng trong INSERT/UPDATE — code cũ set pageId vẫn hoạt động.
     * <p>
     * Nếu cần navigate entity, dùng field page (read-only) bên dưới.
     */
    @Column(name = "page_id", nullable = false)
    private Long pageId;

    /**
     * page: Entity Page (LAZY — chỉ load khi cần).
     * <p>
     * 📌 insertable = false, updatable = false:
     *    JPA dùng pageId để ghi DB, page chỉ để đọc.
     *    Cho phép gọi layer.getPage().getChapterId() trực tiếp.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", insertable = false, updatable = false)
    private Page page;

    /**
     * label: Tên hiển thị của layer (vd: "Base Page", "Background - Tanaka").
     * Khi tạo từ TaskSubmission → lấy từ task.title.
     */
    @Column(nullable = false)
    private String label;

    /**
     * fileUrl: URL ảnh của layer trên Cloudinary.
     * Khi tạo từ TaskSubmission → lấy từ submission.resultImageUrl.
     * Với layer mặc định (Base Page) → lấy từ page.originalImageUrl.
     */
    @Column(name = "file_url")
    private String fileUrl;

    /**
     * thumbnailUrl: URL thumbnail của layer (hiển thị trong LayerPanel).
     */
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    /**
     * sortOrder: Thứ tự hiển thị (0 = dưới cùng).
     * Layer mới từ approve luôn được gán maxSortOrder + 1 (trên cùng).
     */
    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    /**
     * opacity: Độ trong suốt (0.0 → 1.0).
     * Frontend có thể thay đổi qua LayerPanel.
     */
    @Builder.Default
    private double opacity = 1.0;

    /**
     * visible: Có hiển thị layer hay không (toggle mắt trong LayerPanel).
     */
    @Builder.Default
    private boolean visible = true;

    /**
     * blendMode: Chế độ hoà trộn (normal, multiply, screen, ...).
     * Frontend sử dụng Konva để render với blend mode tương ứng.
     */
    @Column(name = "blend_mode")
    @Builder.Default
    private String blendMode = "normal";

    /**
     * locked: Khoá layer — không cho chỉnh sửa (di chuyển, xoá, đổi tên).
     */
    @Builder.Default
    private boolean locked = false;

    /**
     * createdBy: Người tạo layer (ManyToOne → User).
     * Khi tạo từ TaskSubmission → submission.task.assistant (ASSISTANT đó).
     * Khi tạo layer mặc định → MANGAKA (chủ series).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /**
     * createdAt: Thời điểm tạo layer. SQL Server GETDATE() mặc định.
     */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
