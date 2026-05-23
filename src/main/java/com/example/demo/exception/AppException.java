package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ── AppException ──
 * Custom exception cho phép gắn kèm HttpStatus vào lỗi.
 *
 * 📌 Tại sao cần?
 *   RuntimeException chỉ mang message, không mang status code.
 *   Khi throw RuntimeException("Invalid credentials"):
 *     - Spring mặc định trả về 500 Internal Server Error
 *     - Nhưng đây là lỗi 401 Unauthorized!
 *
 * 📌 Cách dùng:
 *   throw new AppException(HttpStatus.CONFLICT, "Email already registered");
 *
 * 📌 @Getter (Lombok):
 *   Tự sinh getter cho field status.
 *   GlobalExceptionHandler gọi ex.getStatus() để lấy HttpStatus.
 *
 * 📌 extends RuntimeException:
 *   Là unchecked exception — không cần khai báo throws trong method.
 *   Spring @RestControllerAdvice sẽ bắt được dù không có try-catch.
 */
@Getter
public class AppException extends RuntimeException {

    /**
     * HttpStatus tương ứng với lỗi:
     *   400 BAD_REQUEST    → validation, đầu vào sai
     *   401 UNAUTHORIZED   → sai email/password
     *   409 CONFLICT       → email/username đã tồn tại
     */
    private final HttpStatus status;

    /**
     * @param status  HTTP status code (400, 401, 409, ...)
     * @param message Mô tả lỗi trả về cho client
     */
    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
