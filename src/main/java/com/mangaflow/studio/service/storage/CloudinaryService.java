package com.mangaflow.studio.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * ── CloudinaryService ──
 * Service xử lý upload & xoá ảnh page lên Cloudinary.
 *
 * 📌 @Service: Spring Bean — đánh dấu lớp này là service.
 * 📌 @RequiredArgsConstructor: Lombok — tự tạo constructor với các
 *    field final (cloudinary), Spring tự inject vào.
 *
 * ══════════════════════════════════════════════════════════════════
 *  Luồng hoạt động:
 * ══════════════════════════════════════════════════════════════════
 *  1️⃣ PageService nhận file ảnh từ frontend
 *  2️⃣ PageService gọi CloudinaryService.uploadPage(file, userId, seriesId, chapterId, pageNumber)
 *  3️⃣ Cloudinary upload file lên server Cloudinary
 *  4️⃣ Cloudinary trả về kết quả (URL, kích thước, ...)
 *  5️⃣ CloudinaryService đóng gói kết quả → UploadResult
 *  6️⃣ PageService lưu URLs vào database
 *
 * ══════════════════════════════════════════════════════════════════
 *  Cấu trúc thư mục trên Cloudinary:
 * ══════════════════════════════════════════════════════════════════
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │ manga_studio/              ← thư mục gốc của dự án          │
 *  │  └── u{userId}/            ← thư mục của tác giả (user ID)  │
 *  │       └── s{seriesId}/     ← thư mục của series (series ID) │
 *  │            └── ch{chapterId}/ ← thư mục của chapter         │
 *  │                 ├── p{pageNumber}.jpg  ← file ảnh page      │
 *  │                 ├── p{pageNumber+1}.jpg                     │
 *  │                 └── ...                                      │
 *  └─────────────────────────────────────────────────────────────┘
 *
 *  Ví dụ thực tế:
 *    manga_studio/u3/s1/ch5/p1   → page 1 của chapter 5, series 1, user 3
 *    manga_studio/u3/s1/ch5/p2   → page 2 của chapter 5, series 1, user 3
 *    manga_studio/u5/s2/ch10/p1  → page 1 của chapter 10, series 2, user 5
 *
 *  Giải thích từng cấp:
 *    - manga_studio     : tên dự án (cố định, không đổi)
 *    - u{userId}        : user ID của tác giả (vd: u3 = user có id = 3)
 *    - s{seriesId}      : series ID (vd: s1 = series có id = 1)
 *    - ch{chapterId}    : chapter ID (vd: ch5 = chapter có id = 5)
 *    - p{pageNumber}    : số thứ tự page trong chapter
 *
 *  📌 Tại sao dùng ID thay vì tên?
 *     - Tên có thể thay đổi (đổi username, sửa title series)
 *     - ID là duy nhất và không bao giờ thay đổi
 *     - Tránh lỗi đường dẫn khi có 2 series trùng tên
 *
 *  📌 Khi nào có Layer?
 *     Sau này mỗi page có thể có nhiều layer (ảnh gốc, layer vẽ, ...)
 *     Cấu trúc sẽ mở rộng thành:
 *       manga_studio/u{userId}/s{seriesId}/ch{chapterId}/p{pageNumber}/l{layerId}
 *
 * ══════════════════════════════════════════════════════════════════
 *  3 URL cho mỗi ảnh:
 * ══════════════════════════════════════════════════════════════════
 *  ┌────────────────┬────────────────────────────────────────────┐
 *  │ URL            │ Mục đích                                    │
 *  ├────────────────┼────────────────────────────────────────────┤
 *  │ originalUrl    │ Ảnh gốc, full size — dùng trong workspace   │
 *  │ webUrl (w_1920)│ Ảnh resize 1920px — dùng hiển thị web      │
 *  │ thumbUrl(w_320)│ Ảnh thumbnail 320px — dùng danh sách pages │
 *  └────────────────┴────────────────────────────────────────────┘
 *
 *  Các URL này có dạng:
 *  https://res.cloudinary.com/{cloudName}/image/upload/{transform}/v{version}/{publicId}.{format}
 *
 *  Trong đó:
 *  - {cloudName} : tên tài khoản Cloudinary (dklp7kcl9)
 *  - {transform} : transformation (crop, resize, ...)
 *    Ví dụ: "c_limit,w_1920" = crop limit, width 1920px
 *  - {version}   : version của ảnh (số timestamp)
 *  - {publicId}  : đường dẫn ảnh (vd: manga_studio/u3/s1/ch5/p1)
 *  - {format}    : đuôi file (jpg, png, ...)
 */
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    /**
     * cloudinary: Object kết nối Cloudinary — được inject từ CloudinaryConfig.
     * Cung cấp method:
     *   - uploader().upload(fileBytes, params)  → upload ảnh
     *   - uploader().destroy(publicId, params)  → xoá ảnh
     */
    private final Cloudinary cloudinary;

    /**
     * Upload 1 file ảnh page lên Cloudinary.
     *
     * 📌 MultipartFile là gì?
     *    Là object Spring dùng để nhận file từ HTTP request.
     *    Khi frontend gửi file qua form-data, Spring tự động
     *    chuyển thành MultipartFile.
     *
     * 📌 Quy trình:
     *    1. Xác định đường dẫn lưu trên Cloudinary (publicId)
     *    2. Upload file lên Cloudinary
     *    3. Đọc kết quả trả về (URL, kích thước, ...)
     *    4. Tạo 3 URLs: original (gốc), web (1920px), thumbnail (320px)
     *    5. Trả về UploadResult chứa URLs + kích thước
     *
     * 📌 folder + filename được tạo theo cấu trúc:
     *    folder    = manga_studio/u{userId}/s{seriesId}/ch{chapterId}
     *    public_id = p{pageNumber}
     *
     *    Ví dụ: userId=3, seriesId=1, chapterId=5, pageNumber=1
     *    → folder = "manga_studio/u3/s1/ch5"
     *    → public_id = "p1"
     *    → Cloudinary tự tạo thư mục + lưu file như:
     *       📁 manga_studio/ → 📁 u3/ → 📁 s1/ → 📁 ch5/ → 🖼️ p1.jpg
     *
     * @param file        File ảnh từ frontend (MultipartFile)
     * @param userId      ID của user (tác giả) — lấy từ JWT token
     * @param seriesId    ID của series — từ URL param
     * @param chapterId   ID của chapter — từ URL param
     * @param pageNumber  Số thứ tự page trong chapter
     * @return UploadResult chứa URLs + kích thước + publicId
     */
    public UploadResult uploadPage(MultipartFile file, Long userId, Long seriesId, Long chapterId, Integer pageNumber) {
        try {
            // ── Xây dựng folder + filename ──
            // Dùng folder param để Cloudinary luôn tạo thư mục,
            // không cần bật "Auto-create folders" trong Settings.
            //
            // Cấu trúc thư mục:
            //   manga_studio/u{userId}/s{seriesId}/ch{chapterId}/p{pageNumber}.jpg
            //
            // folder = "manga_studio/u3/s1/ch5"
            // public_id = "p1" (chỉ tên file, không có path)
            String folder = "manga_studio/u" + userId + "/s" + seriesId
                    + "/ch" + chapterId;
            String filename = "p" + pageNumber;

            // ── Bước 1: Upload file lên Cloudinary ──
            // folder + public_id → Cloudinary tự tạo thư mục nếu chưa có
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", filename,
                            "resource_type", "image",
                            "overwrite", true
                    )
            );

            // ── Bước 2: Đọc kết quả từ Cloudinary ──
            // Cloudinary trả về full publicId (folder + filename)
            // VD: "manga_studio/u3/s1/ch5/p1"
            String resultPublicId = (String) uploadResult.get("public_id");
            String version = String.valueOf(uploadResult.get("version"));
            String format = (String) uploadResult.get("format");
            int width = ((Number) uploadResult.get("width")).intValue();
            int height = ((Number) uploadResult.get("height")).intValue();

            // ── Bước 3: Lấy cloudName từ config ──
            String cloudName = (String) cloudinary.config.cloudName;

            // ── Bước 4: Tạo 3 URLs với các kích thước khác nhau ──
            // Format URL Cloudinary:
            //   https://res.cloudinary.com/{cloudName}/image/upload/{transform}/v{version}/{publicId}.{format}
            //
            // {transform} là phần "transformation" — quyết định ảnh được xử lý thế nào:
            //
            //   1. originalUrl: KHÔNG có transform
            //      → ảnh gốc, full chất lượng, full kích thước
            //      → dùng khi mở ảnh trong workspace để vẽ
            //
            //   2. webUrl: transform = "c_limit,w_1920"
            //      → resize ảnh xuống width tối đa 1920px
            //      → "c_limit" = crop limit: giữ nguyên tỷ lệ, chỉ nhỏ lại nếu ảnh > 1920px
            //      → dùng khi hiển thị ảnh trên web (trình duyệt không cần ảnh 6000px)
            //
            //   3. thumbUrl: transform = "c_limit,w_320"
            //      → resize ảnh xuống width tối đa 320px
            //      → dùng làm thumbnail trong danh sách pages (load nhanh)
            //
            // 🎯 Lợi ích của Cloudinary transformations:
            //    Không cần lưu 3 file riêng biệt. Cloudinary tự động tạo ra
            //    các version resize từ 1 file gốc duy nhất khi request đến URL.
            //    Tiết kiệm dung lượng lưu trữ và băng thông.
            String baseUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/v" + version + "/" + resultPublicId + "." + format;
            String webUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_1920/v" + version + "/" + resultPublicId + "." + format;
            String thumbUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_320/v" + version + "/" + resultPublicId + "." + format;

            // ── Bước 5: Trả về kết quả ──
            return new UploadResult(baseUrl, webUrl, thumbUrl, width, height, resultPublicId);

        } catch (IOException e) {
            // IOException có thể xảy ra khi:
            //   - file.getBytes() lỗi (file hỏng, không đọc được)
            //   - Mạng lỗi khi gọi Cloudinary API
            //   - Cloudinary server trả về lỗi
            //
            // Throw RuntimeException để GlobalExceptionHandler bắt và
            // trả về response lỗi cho frontend
            throw new RuntimeException("Failed to upload file to Cloudinary", e);
        }
    }

    /**
     * Xoá 1 ảnh khỏi Cloudinary.
     *
     * 📌 Dùng khi:
     *    - Xoá page → cần xoá luôn ảnh trên Cloudinary để tránh rác
     *    - Upload lại ảnh mới → cần xoá ảnh cũ trước
     *
     * 📌 cloudinary.uploader().destroy(publicId, options):
     *    Method này gọi API Cloudinary để xoá file.
     *    - publicId: đường dẫn ảnh cần xoá (vd: "manga_studio/u3/s1/ch5/p1")
     *    - options: Map các tuỳ chọn (ObjectUtils.emptyMap() = không có tuỳ chọn gì thêm)
     *
     * 📌 Lưu ý:
     *    publicId KHÔNG bao gồm đuôi file (.jpg) và version.
     *    Chỉ là đường dẫn: "manga_studio/u3/s1/ch5/p1"
     *
     * @param publicId Đường dẫn ảnh trên Cloudinary (vd: "manga_studio/u3/s1/ch5/p1")
     */
    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file from Cloudinary", e);
        }
    }

    /**
     * ── UploadResult ──
     * Class chứa kết quả upload ảnh lên Cloudinary.
     * Đây là DTO (Data Transfer Object) — object dùng để truyền dữ liệu
     * giữa các tầng (CloudinaryService → PageService → PageController → Frontend).
     *
     * 📌 @Value (Lombok): tự động tạo:
     *    - Constructor với tất cả fields (bắt buộc phải truyền đủ 6 tham số)
     *    - Getter cho mỗi field (getOriginalImageUrl(), getWidth(), ...)
     *    - equals(), hashCode(), toString()
     *    - Giống @Data nhưng immutable — không thể thay đổi giá trị sau khi tạo
     *
     * ════════════════════════════════════════════════════════════
     *  Các field:
     * ════════════════════════════════════════════════════════════
     *  ┌──────────────────┬────────────┬──────────────────────────────────────┐
     *  │ Field            │ Kiểu       │ Ví dụ                                │
     *  ├──────────────────┼────────────┼──────────────────────────────────────┤
     *  │ originalImageUrl │ String     │ https://.../v1234/.../p1.jpg         │
     *  │ webImageUrl      │ String     │ https://.../w_1920/v1234/.../p1.jpg  │
     *  │ thumbnailUrl     │ String     │ https://.../w_320/v1234/.../p1.jpg   │
     *  │ width            │ int        │ 4200                                 │
     *  │ height           │ int        │ 6000                                 │
     *  │ publicId         │ String     │ "manga_studio/u3/s1/ch5/p1"          │
     *  └──────────────────┴────────────┴──────────────────────────────────────┘
     */
    @Value
    public static class UploadResult {
        String originalImageUrl;
        String webImageUrl;
        String thumbnailUrl;
        int width;
        int height;
        String publicId;
    }
}
