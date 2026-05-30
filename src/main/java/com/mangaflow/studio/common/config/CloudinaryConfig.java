package com.mangaflow.studio.common.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ── CloudinaryConfig ──
 * File cấu hình để tạo "Cloudinary bean" — một object đại diện cho tài khoản
 * Cloudinary của bạn, giúp backend có thể upload/xoá ảnh lên Cloudinary.
 *
 * 📌 @Configuration: Đánh dấu đây là file cấu hình Spring.
 *    Spring sẽ đọc file này khi chạy và tự động tạo các Bean bên trong.
 *
 * 📌 "Bean" là gì?
 *    Là object được Spring quản lý. Khi bạn khai báo @Bean,
 *    Spring tự tạo object đó và inject (tiêm) vào các class khác
 *    khi cần — bạn không phải tự new object.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Cloudinary là gì?
 * ══════════════════════════════════════════════════════════════════
 *  Cloudinary là dịch vụ lưu trữ ảnh trên cloud (giống Google Drive
 *  nhưng chuyên cho ảnh). Khi bạn upload 1 file ảnh lên Cloudinary,
 *  nó trả về cho bạn 1 URL (đường dẫn) để truy cập ảnh đó.
 *
 *  Lợi ích: Không cần lưu ảnh trên server của mình, tự động tối ưu
 *  ảnh (nén, resize), load nhanh qua CDN (Content Delivery Network).
 *
 *  3 thông tin cần để kết nối Cloudinary:
 *    ┌─────────────────────┬──────────────────────────────────────┐
 *    │ Thông tin           │ Lấy từ đâu?                          │
 *    ├─────────────────────┼──────────────────────────────────────┤
 *    │ cloud-name          │ Dashboard Cloudinary của bạn         │
 *    │ api-key             │ Settings → API Keys                  │
 *    │ api-secret          │ Settings → API Keys                  │
 *    └─────────────────────┴──────────────────────────────────────┘
 *
 *  Các giá trị này được đọc từ file application.properties
 *  thông qua @Value("${tên.property}").
 */
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    /**
     * Tạo Cloudinary bean — object này sẽ được inject vào CloudinaryService.
     *
     * 📌 ObjectUtils.asMap(...):
     *    Tạo Map chứa các config cho Cloudinary.
     *    Giống như bạn tạo 1 Map bằng tay:
     *      Map<String, String> config = new HashMap<>();
     *      config.put("cloud_name", "dklp7kcl9");
     *      ...
     *
     * 📌 new Cloudinary(config):
     *    Tạo object Cloudinary từ config map.
     *    Object này có các method:
     *      - cloudinary.uploader().upload(file, params)  → upload ảnh
     *      - cloudinary.uploader().destroy(publicId, params) → xoá ảnh
     *
     * @return Cloudinary instance sẵn sàng để dùng
     */
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }
}
