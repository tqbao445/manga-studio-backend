package com.example.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * ── JwtUtil ──
 * Công cụ xử lý JWT (JSON Web Token).
 *
 * 📌 JWT là gì?
 *   Một chuỗi token gồm 3 phần (header.payload.signature) dùng để
 *   xác thực request. Server ký token bằng secret key → client gửi
 *   kèm trong header Authorization → server verify.
 *
 * 📌 Luồng hoạt động:
 *   1. User login thành công → server gọi generateToken() → trả JWT
 *   2. Client lưu JWT (localStorage), gửi kèm mọi request
 *   3. JwtAuthFilter đọc token → gọi validateToken() → nếu OK cho qua
 *
 * 📌 @Component:
 *   Đánh dấu class là Spring Bean → có thể @Inject vào class khác.
 *
 * 📌 @Value("${...}"):
 *   Đọc giá trị từ file application.properties.
 *   Constructor Injection → Spring tự động inject khi khởi tạo bean.
 *
 * 📌 Cấu trúc 1 JWT:
 *   {
 *     "sub": "email@example.com",     // subject (chủ thể)
 *     "userId": 1,                     // custom claim
 *     "role": "MANGAKA",               // custom claim
 *     "iat": 1715000000,               // issued at (thời điểm tạo)
 *     "exp": 1715086400                // expiration (hết hạn)
 *   }
 */
@Component
public class JwtUtil {

    /**
     * SecretKey: Key dùng để ký và verify JWT.
     * Keys.hmacShaKeyFor() → tạo key an toàn từ chuỗi string.
     * Phải dài tối thiểu 256 bits (32 ký tự ASCII) cho HS512.
     */
    private final SecretKey secretKey;

    /** Thời hạn token (milliseconds). 86400000ms = 24 giờ */
    private final long expiration;

    /**
     * Constructor Injection: Spring tự động inject giá trị từ
     * application.properties nhờ @Value.
     */
    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * ── generateToken() ──
     * Tạo JWT mới với các thông tin:
     *   - subject: email (định danh chính)
     *   - claim "userId": ID user (để backend biết ai đang gọi API)
     *   - claim "role": Role user (để phân quyền)
     *   - issuedAt: thời điểm tạo
     *   - expiration: thời điểm hết hạn = now + expiration
     *   - signWith: ký token bằng secretKey (thuật toán HS512)
     *
     * 📌 Jwts.builder() → fluent API, compact() → xuất chuỗi JWT
     */
    public String generateToken(Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /** Lấy email từ token (dùng trong filter). */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** Lấy userId từ token. */
    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /** Lấy role từ token. */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * ── validateToken() ──
     * Kiểm tra token có hợp lệ không:
     *   - Đúng signature (chứng minh token do server tạo)
     *   - Chưa hết hạn (exp > now)
     * Nếu parse được claims → hợp lệ; bắt exception → không hợp lệ.
     *
     * 📌 JwtException → tất cả lỗi JWT (sai signature, hết hạn, malformed...)
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * ── parseClaims() (private) ──
     * Giải mã và xác thực token:
     *   - verifyWith(secretKey): kiểm tra chữ ký
     *   - parseSignedClaims(): giải mã payload
     *   - getPayload(): trả về Claims object chứa tất cả thông tin
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
