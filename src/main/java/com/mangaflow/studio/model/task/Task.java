package com.mangaflow.studio.model.task;

import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.region.Region;
import com.mangaflow.studio.model.region.RegionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ── Task Entity ──
 * Ánh xạ tới bảng "tasks" trong database.
 * Mỗi Task là 1 công việc MANGAKA giao cho ASSISTANT,
 * gắn với 1 region cụ thể trên 1 page.
 * <p>
 * 📌 @Entity: JPA entity → Hibernate tạo bảng "tasks"
 * <p>
 * 📌 Quan hệ:
 *    - Region (N:1): task thuộc về region nào
 *    - User.assistant (N:1): ai được giao làm
 *    - User.assignedBy (N:1): ai giao việc (MANGAKA)
 *    - TaskSubmission (1:N): lịch sử nộp bài
 *    - TaskAttachment (1:N): file đính kèm tham khảo
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Luồng đời sống (Lifecycle):
 * ══════════════════════════════════════════════════════════════════
 *  1. MANGAKA tạo task → status = TODO
 *  2. ASSISTANT nhận → status = IN_PROGRESS
 *  3. ASSISTANT nộp bài → submission được tạo (status không đổi)
 *  4. MANGAKA duyệt:
 *     - APPROVED         → task → DONE
 *     - REVISION_REQUIRED → task → IN_PROGRESS (sửa lại)
 *  5. MANGAKA có thể từ chối giữa chừng → REJECTED
 *  6. ASSISTANT làm lại task REJECTED → IN_PROGRESS
 */
@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     * Mỗi task có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * regions: Các region được giao trong task này.
     * 1 task có thể chứa nhiều regions (1:N).
     * Mỗi region chỉ thuộc về 1 task duy nhất.
     * <p>
     * 📌 @OneToMany(mappedBy = "task"):
     *    mappedBy = "task" → Region entity giữ khoá ngoại (task_id).
     *    LAZY = chỉ load regions khi cần.
     * <p>
     * ❌ KHÔNG dùng CascadeType.ALL hay orphanRemoval — Region là entity độc lập,
     *    không bị ràng buộc vòng đời bởi Task. Xoá Task không được xoá Region.
     */
    @OneToMany(mappedBy = "task", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private Set<Region> regions = new LinkedHashSet<>();

    /**
     * title: Tiêu đề công việc.
     * NOT NULL — bắt buộc, max 255 ký tự (validate ở DTO).
     * <p>
     * VD: "Vẽ nhân vật chính panel 3"
     */
    @Column(nullable = false)
    private String title;

    /**
     * regionType: Loại vùng (BACKGROUND, CHARACTER...).
     * NULLABLE — không bắt buộc, có thể override regionType của region gốc.
     * <p>
     * 📌 Nếu null → frontend có thể lấy từ region gốc.
     *    Nếu có giá trị → override regionType mặc định.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "region_type")
    private RegionType regionType;

    /**
     * description: Mô tả chi tiết công việc.
     * TEXT type → không giới hạn 255 ký tự.
     * NULLABLE — không bắt buộc.
     */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /**
     * notes: Ghi chú thêm cho ASSISTANT.
     * TEXT type → không giới hạn 255 ký tự.
     * NULLABLE — không bắt buộc.
     */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    /**
     * referenceImageUrl: URL ảnh tham khảo.
     * MANGAKA có thể đính kèm 1 ảnh mẫu cho ASSISTANT tham khảo.
     * NULLABLE — không bắt buộc.
     */
    private String referenceImageUrl;

    /**
     * pageImageUrl: URL ảnh của page (webImageUrl).
     * Tự động copy từ region → page khi tạo task.
     * ASSISTANT dùng ảnh này để biết context.
     * <p>
     * 📌 Denormalized field:
     *    Copy từ Page.webImageUrl để tránh join nhiều bảng
     *    mỗi lần hiển thị danh sách task.
     */
    @Column(name = "page_image_url", length = 500)
    private String pageImageUrl;

    /**
     * status: Trạng thái hiện tại của task.
     * Mặc định: TODO (vừa tạo).
     * <p>
     * 📌 @Enumerated(EnumType.STRING):
     *    Lưu tên enum dạng chữ (VD: "TODO"), không phải số.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    /**
     * priority: Mức độ ưu tiên.
     * Mặc định: MEDIUM.
     * <p>
     * 📌 @Builder.Default:
     *    Khi dùng builder mà không set priority → mặc định MEDIUM.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    /**
     * assistant: Người được giao làm task (ASSISTANT).
     * N:1 với User — nullable = false.
     * <p>
     * 📌 @ManyToOne(fetch = FetchType.LAZY):
     *    LAZY = chỉ load khi cần (tiết kiệm query).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_id")
    private User assistant;

    /**
     * assignedBy: Người giao việc (MANGAKA).
     * N:1 với User — NOT NULL.
     * <p>
     * 📌 Luôn là user đang đăng nhập khi tạo task.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    /**
     * assignedAt: Thời điểm giao việc (hoặc giao lại).
     * NOT NULL — set lúc tạo task hoặc khi đổi assistant.
     * <p>
     * 📌 Nếu update task và đổi assistantId:
     *    assignedAt được reset = now (giao lại cho người mới).
     */
    @Column(nullable = false)
    private LocalDateTime assignedAt;

    /**
     * dueDate: Hạn chót.
     * NULLABLE — không bắt buộc.
     * Nếu có → phải ở tương lai (validate ở Service).
     */
    private LocalDateTime dueDate;

    /**
     * createdAt: Thời điểm tạo task.
     * @Column(updatable = false) → không cho UPDATE sau insert.
     * Set tự động trong @PrePersist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * submissions: Danh sách các lần nộp bài (lịch sử).
     * 1:N với TaskSubmission — cascade ALL.
     * <p>
     * 📌 orphanRemoval = true:
     *    Nếu xoá submission khỏi list → Hibernate tự động DELETE.
     * <p>
     * 📌 @Builder.Default:
     *    Khởi tạo ArrayList rỗng tránh NullPointerException.
     *    Khi tạo task mới → submissions rỗng (chưa có ai nộp).
     */
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private List<TaskSubmission> submissions = new ArrayList<>();

    /**
     * attachments: Danh sách file đính kèm.
     * 1:N với TaskAttachment — cascade ALL.
     * <p>
     * 📌 orphanRemoval = true:
     *    Xoá attachment khỏi list → Hibernate tự DELETE record.
     * <p>
     * 📌 @Builder.Default:
     *    Khởi tạo ArrayList rỗng tránh NullPointerException.
     */
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private List<TaskAttachment> attachments = new ArrayList<>();

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set createdAt cho task.
     * <p>
     * 📌 Không cần gọi thủ công ở Service.
     *    Hibernate tự gọi method này khi save().
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
