package com.mangaflow.studio.model.region;

import com.mangaflow.studio.model.task.Task;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── Region Entity ──
 * Ánh xạ tới bảng "region" trong database.
 * Mỗi Region là 1 vùng được đánh dấu trên page,
 * tương ứng với 1 thành phần (background, nhân vật, chữ, hiệu ứng...)
 * mà MANGAKA muốn giao cho ASSISTANT vẽ.
 * <p>
 * 📌 @Entity: Đánh dấu class này là 1 JPA entity
 *    → Hibernate tự động tạo/làm việc với bảng "region"
 * <p>
 * 📌 @Table(name = "region"):
 *    Tên bảng trong database là "region"
 * <p>
 * 📌 @Data (Lombok):
 *    Tự sinh getter, setter, toString, equals, hashCode
 * <p>
 * 📌 @NoArgsConstructor:
 *    JPA cần constructor không tham số
 * <p>
 * 📌 @AllArgsConstructor:
 *    Tiện cho việc tạo object (dùng trong test)
 * <p>
 * 📌 @Builder:
 *    Pattern cho phép tạo object kiểu:
 *    Region.builder().regionType(...).x(...).y(...).build()
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Quan hệ với các entity khác:
 * ══════════════════════════════════════════════════════════════════
 *  - Page: region.pageId → page.id (nhiều regions thuộc 1 page)
 *  - Task: task.regionId → region.id (1 region có nhiều tasks)
 * <p>
 *  pageId hiện tại là Long field đơn thuần (không có @ManyToOne).
 *  Khi cần, có thể thêm quan hệ:
 *    @ManyToOne(fetch = FetchType.LAZY)
 *    @JoinColumn(name = "page_id", nullable = false,
 *                insertable = false, updatable = false)
 *    private Page page;
 *  và giữ lại field pageId để không phá code hiện tại.
 */
@Entity
@Table(name = "region")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Region {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     * Mỗi region có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * pageId: ID của page chứa region này.
     * NOT NULL — mỗi region phải thuộc về 1 page.
     * <p>
     * 📌 Là Long field đơn thuần, KHÔNG phải quan hệ JPA.
     *    Khi nào có Page entity thì mới thêm @ManyToOne.
     */
    @Column(name = "page_id", nullable = false)
    private Long pageId;

    /**
     * regionType: Loại vùng (BACKGROUND, CHARACTER, TEXT...).
     * NOT NULL — region nào cũng có loại.
     * <p>
     * 📌 @Enumerated(EnumType.STRING):
     *    Lưu tên enum dạng chữ (VD: "CHARACTER"), không phải số.
     *    Dễ đọc trong database và dễ debug.
     * <p>
     * 📌 @Column(name = "region_type"):
     *    Tên cột trong DB là "region_type" (snake_case).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "region_type", nullable = false)
    private RegionType regionType;

    /**
     * label: Tên hiển thị của region (VD: "Castle background").
     * NULLABLE — người dùng có thể đặt tên hoặc để trống.
     * <p>
     * 📌 Hiển thị trong RegionPanel của workspace.
     *    Nếu label null → hiển thị tên regionType thay thế.
     */
    private String label;

    /**
     * x: Toạ độ X góc trên bên trái (pixel).
     * NOT NULL — bắt buộc phải có khi vẽ region.
     * <p>
     * 📌 Tính từ mép trái của ảnh page gốc.
     *    VD: x = 500 → region cách mép trái 500px.
     */
    @Column(nullable = false)
    private Integer x;

    /**
     * y: Toạ độ Y góc trên bên trái (pixel).
     * NOT NULL — bắt buộc phải có khi vẽ region.
     * <p>
     * 📌 Tính từ mép trên của ảnh page gốc.
     *    VD: y = 800 → region cách mép trên 800px.
     */
    @Column(nullable = false)
    private Integer y;

    /**
     * width: Chiều rộng của region (pixel).
     * NOT NULL — bắt buộc phải có.
     * <p>
     * 📌 Tính từ toạ độ x sang bên phải.
     *    VD: x=500, width=800 → region từ pixel 500 đến 1300.
     */
    @Column(nullable = false)
    private Integer width;

    /**
     * height: Chiều cao của region (pixel).
     * NOT NULL — bắt buộc phải có.
     * <p>
     * 📌 Tính từ toạ độ y xuống dưới.
     *    VD: y=800, height=1200 → region từ pixel 800 đến 2000.
     */
    @Column(nullable = false)
    private Integer height;

    /**
     * color: Màu hiển thị của region trên canvas (hex, VD: "#FF6B6B").
     * NULLABLE — nếu không gửi → backend tự gán màu theo regionType.
     * <p>
     * 📌 @Column(length = 7):
     *    Độ dài tối đa 7 ký tự (VD: "#FF6B6B").
     * <p>
     * 📌 Dùng để vẽ khung viền region trên canvas.
     *    Mỗi regionType có 1 màu mặc định:
     *    - BACKGROUND → #4ECDC4 (xanh ngọc)
     *    - CHARACTER  → #FF6B6B (đỏ)
     *    - TEXT       → #FFE66D (vàng)
     *    - EFFECT     → #A78BFA (tím)
     *    - TONE       → #6B7280 (xám)
     *    - OTHER      → #6B7280 (xám)
     */
    @Column(length = 7)
    private String color;

    /**
     * task: Task mà region này được giao.
     * Nhiều regions có thể thuộc cùng 1 task (N:1).
     * NULLABLE — region có thể chưa được giao task nào.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @EqualsAndHashCode.Exclude
    private Task task;

    /**
     * sortOrder: Thứ tự render của region (0 = dưới cùng).
     * DEFAULT 0 — khi tạo mới, backend tự gán max + 1.
     * <p>
     * 📌 @Builder.Default:
     *    Khi dùng builder mà không set sortOrder → mặc định 0.
     * <p>
     * 📌 Dùng để sắp xếp thứ tự hiển thị trong RegionPanel.
     *    Khi kéo thả region trong panel → gọi reorder API.
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * createdAt: Thời điểm tạo region.
     * @Column(updatable = false) → không cho UPDATE sau khi insert.
     * Set tự động trong @PrePersist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * <p>
     * Set createdAt cho region.
     * 📌 Không cần gọi thủ công ở Service layer.
     *    Hibernate tự gọi method này khi save().
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
