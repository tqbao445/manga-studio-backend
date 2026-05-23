package com.example.demo.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ── GlobalExceptionHandler ──
 * Bắt và xử lý mọi exception trong toàn bộ ứng dụng.
 *
 * 📌 @RestControllerAdvice:
 *   = @ControllerAdvice + @ResponseBody
 *   → Áp dụng cho TẤT CẢ controller trong project.
 *   → Mọi method trả về JSON (không cần @ResponseBody riêng).
 *
 * 📌 @ExceptionHandler(XxxException.class):
 *   Đánh dấu method xử lý cho một loại exception cụ thể.
 *   Spring tự động gọi method phù hợp dựa trên kiểu exception.
 *
 * 📌 Thứ tự ưu tiên:
 *   Spring chọn handler "gần nhất" trong cây kế thừa.
 *   - AppException → handleAppException (ưu tiên hơn Exception)
 *   - RuntimeException extends Exception → vẫn rơi vào handleGeneral
 *
 * 📌 Luồng xử lý:
 *   Exception ném ra → Spring tìm @ExceptionHandler phù hợp
 *   → Gọi method → Trả về ResponseEntity<ErrorResponse>
 *
 * 📌 Tại sao nên dùng @RestControllerAdvice thay vì try-catch?
 *   - Không phải viết try-catch trong từng controller
 *   - Exception xử lý tập trung, dễ maintain
 *   - Format response đồng nhất
 *   - Dễ thêm loại lỗi mới (chỉ cần thêm 1 method)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * ── handleAppException() ──
     * Bắt AppException — lỗi do service throw có HttpStatus cụ thể.
     *
     * Ví dụ:
     *   throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
     *   → status=401, error="Unauthorized", message="Invalid credentials"
     *
     * 📌 ex.getStatus(): Lấy HttpStatus đã gắn trong AppException.
     * 📌 ResponseEntity.status(): Set HTTP status cho response.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("App exception: {} - {}", ex.getStatus(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getMessage());
    }

    /**
     * ── handleValidation() ──
     * Bắt MethodArgumentNotValidException — lỗi @Valid không pass.
     *
     * 📌 Khi nào xảy ra?
     *   Khi DTO có @NotBlank, @Email, @Size... mà dữ liệu không hợp lệ.
     *   Ví dụ: email rỗng → @NotBlank → ném exception.
     *
     * 📌 ex.getBindingResult().getFieldErrors():
     *   Danh sách lỗi của từng field trong DTO.
     *   Mỗi FieldError có getField() (tên field) + getDefaultMessage() (message).
     *
     * 📌 Response mẫu:
     *   {
     *     "email": "Email is required",
     *     "password": "Password must be at least 6 characters"
     *   }
     *
     * 📌 Lưu ý: Response dạng Map thay vì ErrorResponse.
     *   Client dễ dàng map field → lỗi tương ứng để hiển thị trên form.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError field : ex.getBindingResult().getFieldErrors()) {
            errors.put(field.getField(), field.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * ── handleNotReadable() ──
     * Bắt HttpMessageNotReadableException — lỗi parse JSON đầu vào.
     *
     * 📌 Khi nào xảy ra?
     *   Jackson không thể parse JSON body thành DTO/Entity.
     *   - Enum sai: "role": "ADMIN" (không có trong Role enum)
     *   - JSON syntax lỗi: thiếu dấu ngoặc, sai kiểu dữ liệu
     *
     * 📌 InvalidFormatException:
     *   Là nguyên nhân gốc (cause) của HttpMessageNotReadableException
     *   khi Jackson không parse được giá trị (vd: string → enum).
     *
     * 📌 ife.getTargetType().isEnum():
     *   Kiểm tra xem mục tiêu parse có phải enum không.
     *   Nếu phải → tự động gợi ý các giá trị hợp lệ.
     *   getEnumConstants() trả về mảng tất cả hằng số của enum.
     *
     * 📌 Tại sao không dùng instanceof Role?
     *   ife.getTargetType() là Class<?> — có thể so sánh với bất kỳ
     *   enum nào. Không cần hardcode Role → tự động với mọi enum.
     *
     * 📌 Response mẫu (role sai):
     *   {
     *     "status": 400,
     *     "error": "Bad Request",
     *     "message": "Invalid Role. Accepted: [MANGAKA, ASSISTANT, TANTOU_EDITOR, EDITORIAL_BOARD]",
     *     "timestamp": "..."
     *   }
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        String message = "Invalid request body";

        // Kiểm tra xem nguyên nhân có phải là InvalidFormatException không
        if (ex.getCause() instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {

            // Tự động lấy tên enum + danh sách giá trị hợp lệ
            message = "Invalid " + ife.getTargetType().getSimpleName()
                    + ". Accepted: " + Arrays.toString(ife.getTargetType().getEnumConstants());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * ── handleGeneral() ──
     * Bắt TẤT CẢ exception còn lại (fallback handler).
     *
     * 📌 @ExceptionHandler(Exception.class):
     *   Exception là class cha của mọi exception trong Java.
     *   Nếu không có handler cụ thể nào, exception sẽ rơi vào đây.
     *
     * 📌 KHÔNG bao giờ trả về stack trace trong production!
     *   - Lộ cấu trúc code, package, đường dẫn file
     *   - Nguy cơ bảo mật
     *   - Thay vào đó log ra console, trả message chung chung
     *
     * 📌 Nếu muốn debug, log ex.getMessage() ra console.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    /**
     * ── buildResponse() (private) ──
     * Method dùng chung để tạo ResponseEntity<ErrorResponse>.
     *
     * 📌 Tại sao cần?
     *   Tránh lặp code — cả 3 handler đều tạo ErrorResponse giống nhau.
     *   Chỉ khác status và message.
     *
     * 📌 ResponseEntity.status(status):
     *   Set HTTP status code cho response.
     *   Spring sẽ trả về status này trong HTTP header.
     */
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())           // 401, 400, 500...
                .error(status.getReasonPhrase())  // "Unauthorized", "Bad Request"...
                .message(message)                 // "Invalid credentials"...
                .timestamp(LocalDateTime.now())   // thời điểm lỗi
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
