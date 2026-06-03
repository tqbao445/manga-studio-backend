package com.mangaflow.studio.model.comment;

/**
 * ── CommentStatus ──
 * Trạng thái của 1 comment trong hệ thống.
 *
 * 📌 ACTIVE:
 *    - Comment đang hoạt động — hiển thị trên page.
 *    - Còn đang chờ xử lý hoặc đang thảo luận.
 *    - Mặc định khi tạo comment mới.
 *
 * 📌 RESOLVED:
 *    - Comment đã được giải quyết xong.
 *    - Vấn đề đã được xử lý (Mangaka hoặc Tantou Editor đánh dấu).
 *    - Vẫn hiển thị để xem lịch sử nhưng có dấu hiệu "đã xong".
 *
 * ══════════════════════════════════════════════════════════════════
 *  Cách dùng:
 * ══════════════════════════════════════════════════════════════════
 *  - Tantou Editor tạo comment ACTIVE (đánh dấu lỗi trên page)
 *  - Mangaka reply/sửa xong → chuyển thành RESOLVED
 *  - Có thể chuyển từ RESOLVED → ACTIVE nếu cần mở lại
 *
 * @Enum STRING trong DB: lưu chữ "ACTIVE" hoặc "RESOLVED"
 */
public enum CommentStatus {
    ACTIVE,
    RESOLVED
}
