package com.mangaflow.studio.model.task;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── TaskAttachment Entity ──
 * Ánh xạ tới bảng "task_attachments" trong database.
 * Mỗi TaskAttachment là 1 file tham khảo (ảnh mẫu, tài liệu hướng dẫn)
 * mà MANGAKA đính kèm vào task để ASSISTANT tham khảo trước khi làm.
 * <p>
 * 📌 @Entity: JPA entity → Hibernate tạo bảng "task_attachments"
 * <p>
 * 📌 Quan hệ:
 *    - Task (N:1): attachment này thuộc task nào
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  So sánh referenceImageUrl vs attachment:
 * ══════════════════════════════════════════════════════════════════
 *  ┌────────────────────┬──────────────────────┬──────────────────────┐
 *  │ Field              │ referenceImageUrl    │ attachment           │
 *  ├────────────────────┼──────────────────────┼──────────────────────┤
 *  │ Số lượng           │ Tối đa 1            │ Nhiều                │
 *  │ Bắt buộc           │ Không               │ Không                │
 *  │ Mục đích           │ Ảnh tham khảo chính  │ File bổ sung         │
 *  │ Xoá                │ Sửa task             │ DELETE /attachments  │
 *  │ Field trong entity │ Task.referenceImgUrl │ TaskAttachment table │
 *  └────────────────────┴──────────────────────┴──────────────────────┘
 */
@Entity
@Table(name = "task_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAttachment {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     * Mỗi attachment có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * task: Task mà attachment này thuộc về.
     * N:1 với Task — cascade ALL (xoá task → xoá attachment).
     * <p>
     * 📌 @ManyToOne(fetch = FetchType.LAZY):
     *    LAZY = chỉ load khi cần.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private Task task;

    /**
     * fileUrl: URL của file đính kèm (trên Cloudinary).
     * NOT NULL — bắt buộc, max 500 ký tự.
     * <p>
     * 📌 MANGAKA upload file lên Cloudinary và gửi URL.
     *    File này có thể là ảnh, PSD, PDF,...
     */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /**
     * fileName: Tên file gốc (vd: "mau-tham-khao.png").
     * Lưu để frontend hiển thị tên file đẹp thay vì URL dài.
     * NULL safe — nếu không lấy được tên, frontend fallback về "file".
     */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /**
     * uploadedAt: Thời điểm upload file.
     * NOT NULL — set lúc tạo attachment.
     */
    @Column(nullable = false)
    private LocalDateTime uploadedAt;
}
