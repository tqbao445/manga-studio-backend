package com.mangaflow.studio.model.task;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ── TaskSubmission Entity ──
 * Ánh xạ tới bảng "task_submissions" trong database.
 * Mỗi TaskSubmission là 1 lần nộp bài của ASSISTANT cho 1 task.
 * <p>
 * 📌 @Entity: JPA entity → Hibernate tạo bảng "task_submissions"
 * <p>
 * 📌 Quan hệ:
 *    - Task (N:1): submission này thuộc task nào
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Luồng nộp bài:
 * ══════════════════════════════════════════════════════════════════
 *  1. ASSISTANT nộp bài → status = SUBMITTED, version tự động tăng
 *  2. MANGAKA duyệt:
 *     - APPROVED          → submission hoàn thành, task → DONE
 *     - REVISION_REQUIRED → submission yêu cầu sửa, task → IN_PROGRESS
 *  3. ASSISTANT sửa xong → nộp lại → version + 1
 * <p>
 * 📌 Version:
 *    - Version 1: lần nộp đầu tiên
 *    - Version 2: lần sửa thứ 1
 *    - Version N: lần sửa thứ N-1
 *    - Mỗi task có thể có nhiều submissions, mỗi submission có 1 version
 *    - Các submissions cũ vẫn được giữ lại để MANGAKA so sánh
 */
@Entity
@Table(name = "task_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskSubmission {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY).
     * Mỗi submission có 1 id duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * task: Task mà submission này thuộc về.
     * N:1 với Task — mỗi submission gắn với đúng 1 task.
     * <p>
     * 📌 @ManyToOne(fetch = FetchType.LAZY):
     *    LAZY = chỉ load khi cần — tránh load cả task không cần thiết.
     * <p>
     * 📌 @JoinColumn(name = "task_id", nullable = false):
     *    Khoá ngoại task_id → tasks.id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    @EqualsAndHashCode.Exclude
    private Task task;

    /**
     * resultImageUrl: URL ảnh kết quả (JPG/PNG).
     * NOT NULL — bắt buộc, max 500 ký tự.
     * <p>
     * 📌 ASSISTANT upload ảnh kết quả lên Cloudinary và gửi URL.
     *    MANGAKA xem ảnh này để đánh giá chất lượng.
     */
    @Column(name = "result_image_url", nullable = false, length = 500)
    private String resultImageUrl;

    /**
     * fileUrl: URL file nguồn (PSD, CLIP, ...).
     * NULLABLE — không bắt buộc, max 500 ký tự.
     * <p>
     * 📌 ASSISTANT có thể gửi file gốc để MANGAKA kiểm tra layer.
     *    File này thường lớn, không hiển thị trực tiếp trên web.
     */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    /**
     * note: Ghi chú của ASSISTANT cho MANGAKA.
     * NULLABLE — không bắt buộc.
     * <p>
     * VD: "Đã vẽ xong nhân vật chính, anh xem giúp em"
     */
    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * reviewNote: Ghi chú của MANGAKA khi duyệt.
     * NULLABLE — không bắt buộc.
     * VD: "Cần sửa màu sắc và thêm shadow"
     */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /**
     * version: Số phiên bản nộp bài.
     * NOT NULL — tự động tăng dần (1, 2, 3...).
     * <p>
     * 📌 Lấy version cao nhất hiện tại + 1.
     *    Nếu chưa có submission nào → version = 1.
     */
    @Column(nullable = false)
    private Integer version;

    /**
     * status: Trạng thái của submission này.
     * Mặc định: SUBMITTED (vừa nộp, chờ duyệt).
     * <p>
     * 📌 @Builder.Default:
     *    Khi dùng builder không set status → mặc định SUBMITTED.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskSubmissionStatus status = TaskSubmissionStatus.SUBMITTED;

    /**
     * submittedAt: Thời điểm nộp bài.
     * NOT NULL — set lúc tạo submission.
     */
    @Column(nullable = false)
    private LocalDateTime submittedAt;
}
