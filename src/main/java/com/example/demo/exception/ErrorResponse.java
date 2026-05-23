package com.example.demo.exception;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ── ErrorResponse ──
 * DTO chuẩn cho mọi response lỗi.
 *
 * 📌 Mục đích:
 *   Đảm bảo mọi lỗi trả về đều có cùng format JSON.
 *   Client (frontend) chỉ cần parse 1 cấu trúc duy nhất.
 *
 * 📌 Response mẫu:
 *   {
 *     "status": 401,                    // HTTP status code
 *     "error": "Unauthorized",          // Tên lỗi chuẩn HTTP
 *     "message": "Invalid credentials", // Mô tả chi tiết
 *     "timestamp": "2026-05-23T14:30"   // Thời điểm lỗi
 *   }
 *
 * 📌 @Builder (Lombok):
 *   Cho phép tạo object kiểu fluent:
 *   ErrorResponse.builder().status(401).error("Unauthorized").build()
 *
 *   Gọn hơn new + setter, dễ đọc hơn constructor nhiều tham số.
 *
 * 📌 LocalDateTime timestamp:
 *   Ghi lại thời điểm lỗi xảy ra — hữu ích cho debug.
 *   Dùng LocalDateTime.now() khi tạo response.
 */
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
}
