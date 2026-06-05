package com.mangaflow.studio.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

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
     *    folder    = manga_studio/chapters/ch{chapterId}/pages/p{pageId}
     *    public_id = "original" (tên cố định)
     *
     *    Ví dụ: chapterId=5, pageId=10
     *    → folder = "manga_studio/chapters/ch5/pages/p10"
     *    → public_id = "original"
     *    → Cloudinary lưu file tại: .../ch5/pages/p10/original.jpg
     *
     * @param file       File ảnh từ frontend (MultipartFile)
     * @param chapterId  ID của chapter — từ URL param
     * @param pageId     ID của page — không đổi, tránh ảnh hưởng khi reorder
     * @return UploadResult chứa URLs + kích thước + publicId
     */
    public UploadResult uploadPage(MultipartFile file, Long chapterId, Long pageId) {
        try {
            // ── Xây dựng folder + filename ──
            // Dùng folder param để Cloudinary luôn tạo thư mục,
            // không cần bật "Auto-create folders" trong Settings.
            //
            // Cấu trúc thư mục:
            //   manga_studio/chapters/ch{chapterId}/pages/p{pageId}/original.jpg
            //
            // folder = "manga_studio/chapters/ch5/pages/p10"
            // public_id = "original" (tên file cố định, ghi đè khi re-upload)
            String folder = "manga_studio/chapters/ch" + chapterId
                    + "/pages/p" + pageId;

            // ── Bước 1: Upload file lên Cloudinary ──
            // folder + public_id → Cloudinary tự tạo thư mục nếu chưa có
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "original",
                            "resource_type", "image",
                            "overwrite", true
                    )
            );

            // ── Bước 2: Đọc kết quả từ Cloudinary ──
            // Cloudinary trả về full publicId (folder + filename)
            // VD: "manga_studio/chapters/ch5/pages/p10/original"
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

    // ════════════════════════════════════════════════════════════════
    //  COVER METHODS (dành cho upload ảnh bìa / thumbnail của Series)
    // ════════════════════════════════════════════════════════════════

    /**
     * ── uploadCover ──
     * Upload ảnh bìa (thumbnail) của series lên Cloudinary.
     *
     * 📌 Cấu trúc thư mục:
     *    manga_studio/u{userId}/s{seriesId}/cover/cover.jpg
     *
     *    Giải thích từng cấp:
     *    - manga_studio        : thư mục gốc của dự án (cố định)
     *    - u{userId}           : user ID của tác giả (vd: u3 = user id = 3)
     *    - s{seriesId}         : series ID (vd: s1 = series id = 1)
     *    - cover/cover.jpg     : file ảnh bìa, luôn có tên "cover"
     *
     * 📌 overwrite = true:
     *    Khi tạo series lần đầu, upload file → public_id = "cover".
     *    Khi update series sau này (đổi ảnh bìa), upload lại file mới
     *    với cùng public_id → Cloudinary tự động ghi đè file cũ.
     *    → Không sinh ra file rác trên Cloudinary.
     *
     * 📌 transformation = "c_fill,w_600":
     *    - c_fill: crop ảnh để fill đúng kích thước, giữ tâm ảnh
     *    - w_600:  width = 600px (đủ lớn cho thumbnail card)
     *    - Chiều cao tự động tính theo tỷ lệ ảnh gốc
     *    → Ảnh thumbnail đồng nhất, load nhanh trên danh sách series.
     *
     * 📌 URL trả về có dạng:
     *    https://res.cloudinary.com/{cloudName}/image/upload/c_fill,w_600/v{version}/manga_studio/u{userId}/s{seriesId}/cover/cover.jpg
     *
     * @param file     File ảnh từ frontend (MultipartFile) — bắt buộc khi tạo series
     * @param userId   ID của user (tác giả) — lấy từ JWT token
     * @param seriesId ID của series — có được sau khi save entity vào DB
     * @return URL đầy đủ của ảnh cover trên Cloudinary (vd: https://res.cloudinary.com/.../cover.jpg)
     */
    public String uploadCover(MultipartFile file, Long userId, Long seriesId) {
        try {
            // ── Xây dựng folder + public_id ──
            // folder = "manga_studio/series/s{seriesId}"
            // public_id = "cover" (tên file cố định, ghi đè khi update)
            //
            // Ví dụ: seriesId=1
            // → folder = "manga_studio/series/s1"
            // → public_id = "cover"
            // → Cloudinary lưu file tại: manga_studio/series/s1/cover.jpg
            String folder = "manga_studio/series/s" + seriesId;

            // ── Upload file lên Cloudinary ──
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "cover",
                            "resource_type", "image",
                            "overwrite", true,
                            "transformation", "c_fill,w_600"
                    )
            );

            // ── Lấy URL từ kết quả trả về ──
            // Cloudinary trả về URL dạng:
            // https://res.cloudinary.com/{cloudName}/image/upload/c_fill,w_600/v{version}/manga_studio/series/s{seriesId}/cover.jpg
            return (String) result.get("url");

        } catch (IOException e) {
            // IOException khi:
            //   - file.getBytes() lỗi (file hỏng, không đọc được)
            //   - Mạng lỗi khi gọi Cloudinary API
            //   - Cloudinary server trả về lỗi
            throw new RuntimeException("Failed to upload cover to Cloudinary", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  AVATAR METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Upload ảnh avatar cho user lên Cloudinary.
     *
     * Cấu trúc thư mục:
     *   manga_studio/u{userId}/profile/avatar.jpg
     *
     * Transformation: c_fill,w_400,h_400
     *   - c_fill: crop vuông, giữa trung tâm ảnh
     *   - w_400,h_400: resize xuống 400x400px
     *
     * @param file   File ảnh từ frontend
     * @param userId ID của user
     * @return URL của ảnh trên Cloudinary (vd: https://res.cloudinary.com/.../avatar.jpg)
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        try {
            String folder = "manga_studio/users/u" + userId;

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "avatar",
                            "resource_type", "image",
                            "overwrite", true,
                            "transformation", "c_fill,w_400,h_400"
                    )
            );

            return (String) result.get("url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload avatar to Cloudinary", e);
        }
    }

    /**
     * Xoá ảnh trên Cloudinary bằng URL (không cần publicId).
     *
     * Tự động parse URL để lấy publicId.
     *
     * URL Cloudinary có dạng:
     *   https://res.cloudinary.com/{cloud}/image/upload/{transform}/v{version}/{publicId}.{ext}
     *
     * Ví dụ:
     *   URL:  https://res.cloudinary.com/dklp7kcl9/image/upload/c_fill,w_400,h_400/v1234/manga_studio/u1/profile/avatar.jpg
     *   → publicId: "manga_studio/u1/profile/avatar"
     *
     * @param imageUrl URL đầy đủ của ảnh trên Cloudinary
     */
    public void deleteImageByUrl(String imageUrl) {
        try {
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                // Phát hiện resource type từ URL:
                //   /image/upload/ → "image"
                //   /raw/upload/   → "raw"
                String resourceType = imageUrl.contains("/raw/upload/") ? "raw" : "image";
                cloudinary.uploader().destroy(publicId,
                        ObjectUtils.asMap("resource_type", resourceType));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image from Cloudinary", e);
        }
    }

    /**
     * Trích xuất publicId từ Cloudinary URL.
     *
     * publicId là phần đường dẫn giữa version và đuôi file:
     *   URL: .../v{version}/{publicId}.{ext}
     *   → publicId = "manga_studio/u1/profile/avatar"
     *
     * @param imageUrl URL Cloudinary đầy đủ
     * @return publicId hoặc null nếu không parse được
     */
    private String extractPublicId(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains("manga_studio")) return null;

        // Tìm vị trí của "manga_studio"
        int start = imageUrl.indexOf("manga_studio");

        // Tìm dấu chấm cuối cùng (phân cách đuôi file)
        int end = imageUrl.lastIndexOf('.');

        if (start == -1 || end == -1 || end <= start) return null;

        return imageUrl.substring(start, end);
    }

    // ════════════════════════════════════════════════════════════════
    //  TASK IMAGE METHODS (dành cho upload ảnh task submission)
    // ════════════════════════════════════════════════════════════════

    /**
     * ── uploadTaskImage ──
     * Upload ảnh kết quả (result image) của TaskSubmission lên Cloudinary.
     * ASSISTANT submit task → backend nhận file ảnh → upload lên Cloudinary.
     *
     * 📌 Cấu trúc thư mục:
     *    manga_studio/u{userId}/tasks/t{taskId}/submissions/v{version}/result
     *
     *    Giải thích từng cấp:
     *    - manga_studio        : thư mục gốc của dự án (cố định)
     *    - u{userId}           : user ID của ASSISTANT (người submit)
     *    - tasks               : thư mục chứa tất cả task images
     *    - t{taskId}           : task ID (vd: t15 = task id = 15)
     *    - submissions         : thư mục chứa các phiên submit
     *    - v{version}          : version của submission (vd: v1 = lần submit đầu)
     *    - result              : tên file ảnh kết quả (public_id = "result")
     *
     * 📌 overwrite = true:
     *    Nếu ASSISTANT submit lại (cùng version) → ghi đè file cũ.
     *    Mỗi lần submit mới → version +1 → không lo mất lịch sử.
     *
     * 📌 Các URL trả về:
     *    - originalImageUrl: ảnh gốc (full size) — dùng trong workspace
     *    - webImageUrl:       ảnh resize 1920px — hiển thị trên web
     *    - thumbnailUrl:      ảnh thumbnail 320px — preview trong danh sách
     *
     * @param file    File ảnh từ frontend (MultipartFile) — kết quả ASSISTANT làm
     * @param userId  ID của ASSISTANT — lấy từ JWT token
     * @param taskId  ID của task — từ URL param
     * @param version Version của submission (bắt đầu từ 1)
     * @return UploadResult chứa URLs + kích thước + publicId
     */
    public UploadResult uploadTaskImage(MultipartFile file, Long userId, Long taskId, int version) {
        try {
            // ── Xây dựng folder + public_id ──
            // folder = "manga_studio/tasks/t{taskId}/submissions/v{version}"
            // public_id = "result" (tên file cố định)
            //
            // Ví dụ: taskId=15, version=1
            // → folder = "manga_studio/tasks/t15/submissions/v1"
            // → public_id = "result"
            // → Cloudinary lưu file tại: manga_studio/tasks/t15/submissions/v1/result.jpg
            String folder = "manga_studio/tasks/t" + taskId
                    + "/submissions/v" + version;

            // ── Upload file lên Cloudinary ──
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "result",
                            "resource_type", "image",
                            "overwrite", true
                    )
            );

            // ── Đọc kết quả từ Cloudinary ──
            String resultPublicId = (String) uploadResult.get("public_id");
            String versionStr = String.valueOf(uploadResult.get("version"));
            String format = (String) uploadResult.get("format");
            int width = ((Number) uploadResult.get("width")).intValue();
            int height = ((Number) uploadResult.get("height")).intValue();

            // ── Lấy cloudName từ config ──
            String cloudName = (String) cloudinary.config.cloudName;

            // ── Tạo 3 URLs ──
            // original: không transform (ảnh gốc)
            // web: w_1920 (hiển thị trên trình duyệt)
            // thumb: w_320 (thumbnail preview)
            String baseUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/v" + versionStr + "/" + resultPublicId + "." + format;
            String webUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_1920/v" + versionStr + "/" + resultPublicId + "." + format;
            String thumbUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_320/v" + versionStr + "/" + resultPublicId + "." + format;

            return new UploadResult(baseUrl, webUrl, thumbUrl, width, height, resultPublicId);

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload task image to Cloudinary", e);
        }
    }

    /**
     * ── uploadTaskRawFile ──
     * Upload file nguồn (source file) của TaskSubmission lên Cloudinary.
     * ASSISTANT có thể gửi kèm file .psd, .clip, .zip,... làm tài liệu tham khảo.
     *
     * 📌 Cấu trúc thư mục (giống uploadTaskImage):
     *    manga_studio/u{userId}/tasks/t{taskId}/submissions/v{version}/source
     *
     * 📌 resource_type = "raw":
     *    Cloudinary phân biệt 2 loại resource:
     *    - "image": file ảnh (jpg, png, ...) — có transform, tạo thumbnail
     *    - "raw":   file bất kỳ (psd, zip, pdf, ...) — giữ nguyên file
     *    Vì source file có thể là .psd, .clip (không phải ảnh) → dùng "raw".
     *
     * 📌 overwrite = true:
     *    Nếu submit lại cùng version → ghi đè file cũ.
     *
     * 💡 Không return UploadResult vì raw file không có width/height.
     *     Chỉ cần URL để hiển thị link download cho MANGAKA.
     *
     * @param file    File nguồn từ frontend (MultipartFile) — có thể là .psd, .zip...
     * @param userId  ID của ASSISTANT — lấy từ JWT token
     * @param taskId  ID của task — từ URL param
     * @param version Version của submission (bắt đầu từ 1)
     * @return URL đầy đủ của file trên Cloudinary
     */
    public String uploadTaskRawFile(MultipartFile file, Long userId, Long taskId, int version) {
        try {
            // ── Xây dựng folder + public_id ──
            // folder = "manga_studio/tasks/t{taskId}/submissions/v{version}"
            // public_id = "source" (tên file cố định)
            //
            // Ví dụ: taskId=15, version=1, file=source.psd
            // → folder = "manga_studio/tasks/t15/submissions/v1"
            // → public_id = "source"
            // → Cloudinary lưu file tại: manga_studio/tasks/t15/submissions/v1/source.psd
            String folder = "manga_studio/tasks/t" + taskId
                    + "/submissions/v" + version;

            // ── Lấy tên file gốc để xác định đuôi mở rộng ──
            // Dùng originalFilename để Cloudinary tự động giữ đuôi file
            // VD: "source.psd" → Cloudinary tạo source.psd
            String originalFilename = file.getOriginalFilename();

            // ── Upload file lên Cloudinary ──
            // resource_type = "raw": cho phép upload file không phải ảnh
            // use_filename = true + unique_filename = false:
            //   → Giữ đúng tên file gốc (VD: source.psd) thay vì Cloudinary tự sinh
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "source",
                            "resource_type", "raw",
                            "overwrite", true
                    )
            );

            return (String) result.get("url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload task raw file to Cloudinary", e);
        }
    }

    /**
     * ── uploadTaskAttachment ──
     * Upload file đính kèm (attachment) của TaskSubmission lên Cloudinary.
     * Tương tự uploadTaskImage nhưng dùng riêng cho attachment
     * (ảnh tham khảo, tài liệu hướng dẫn...).
     *
     * 📌 Cấu trúc thư mục:
     *    manga_studio/tasks/t{taskId}/submissions/v{version}/attachments/{filename}
     *
     * 📌 resource_type = "raw":
     *    Attachment có thể là ảnh hoặc file bất kỳ (pdf, doc...).
     *    Dùng "raw" để hỗ trợ tất cả định dạng.
     *
     * @param file    File đính kèm từ frontend (MultipartFile)
     * @param userId  ID của ASSISTANT — lấy từ JWT token
     * @param taskId  ID của task — từ URL param
     * @param version Version của submission (bắt đầu từ 1)
     * @return URL đầy đủ của file trên Cloudinary
     */
    public String uploadTaskAttachment(MultipartFile file, Long userId, Long taskId, int version) {
        try {
            // ── Xây dựng folder + public_id ──
            // folder = "manga_studio/tasks/t{taskId}/submissions/v{version}/attachments"
            // public_id = tên file gốc (giữ nguyên để dễ nhận biết)
            String folder = "manga_studio/tasks/t" + taskId
                    + "/submissions/v" + version + "/attachments";

            String originalFilename = file.getOriginalFilename();
            // Bỏ đuôi file vì Cloudinary tự thêm
            String publicId = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
                    : "attachment";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", publicId,
                            "resource_type", "raw",
                            "overwrite", true
                    )
            );

            return (String) result.get("url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload task attachment to Cloudinary", e);
        }
    }

    /**
     * ── uploadTaskReference ──
     * Upload file tham khảo (reference attachment) cho task.
     * File do MANGAKA đính kèm để ASSISTANT tham khảo.
     * <p>
     * 📌 Khác với uploadTaskAttachment (thuộc submission/version):
     *    uploadTaskReference không gắn với version nào — lưu trực tiếp
     *    trong thư mục attachments của task.
     * <p>
     * 📌 Cấu trúc thư mục:
     *    manga_studio/tasks/t{taskId}/references/{filename}
     *
     * @param file   File từ frontend (MultipartFile)
     * @param userId ID của MANGAKA — lấy từ JWT token
     * @param taskId ID của task — từ URL param
     * @return URL đầy đủ của file trên Cloudinary
     */
    public String uploadTaskReference(MultipartFile file, Long userId, Long taskId) {
        try {
            String folder = "manga_studio/tasks/t" + taskId + "/references";

            String originalFilename = file.getOriginalFilename();
            String publicId = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
                    : "reference";

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", publicId,
                            "resource_type", "raw",
                            "overwrite", true
                    )
            );

            return (String) result.get("url");

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload task reference to Cloudinary", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  LAYER IMAGE METHODS (dành cho upload ảnh layer)
    // ════════════════════════════════════════════════════════════════

    /**
     * ── uploadLayerImage ──
     * Upload 1 file ảnh cho layer lên Cloudinary.
     * Được LayerService.createLayer() gọi khi frontend gửi file kèm multipart.
     *
     * 📌 Cấu trúc thư mục:
     *    manga_studio/u{userId}/pages/p{pageId}/layers/{uuid}
     *
     *    Giải thích từng cấp:
     *    - manga_studio  : thư mục gốc của dự án (cố định)
     *    - u{userId}     : user ID của MANGAKA (người tạo layer)
     *    - pages         : thư mục chứa ảnh liên quan đến page
     *    - p{pageId}     : page ID (vd: p10 = page id = 10)
     *    - layers        : thư mục chứa ảnh layers
     *    - {uuid}        : UUID ngẫu nhiên — tránh trùng tên file
     *
     * 📌 public_id là UUID ngẫu nhiên:
     *    - Không dùng tên file gốc (dễ trùng)
     *    - Không overwrite (mỗi layer 1 ảnh riêng)
     *
     * 📌 Các URL trả về:
     *    - originalImageUrl: ảnh gốc (full size) — dùng trong workspace
     *    - webImageUrl:       ảnh resize 1920px — hiển thị trên web
     *    - thumbnailUrl:      ảnh thumbnail 320px — preview trong LayerPanel
     *
     * @param file       File ảnh từ frontend (MultipartFile)
     * @param userId     ID của MANGAKA — lấy từ JWT token
     * @param pageId     ID của page chứa layer
     * @param chapterId  ID của chapter chứa page
     * @return UploadResult chứa URLs + kích thước + publicId
     */
    public UploadResult uploadLayerImage(MultipartFile file, Long userId, Long pageId,
                                          Long chapterId) {
        try {
            // ── Xây dựng folder + public_id (UUID ngẫu nhiên) ──
            // folder = "manga_studio/chapters/ch{chapterId}/pages/p{pageId}/layers"
            // public_id = UUID ngẫu nhiên
            //
            // Ví dụ: chapterId=5, pageId=10
            // → folder = "manga_studio/chapters/ch5/pages/p10/layers"
            // → public_id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            String folder = "manga_studio/chapters/ch" + chapterId
                    + "/pages/p" + pageId + "/layers";
            String publicId = java.util.UUID.randomUUID().toString();

            // ── Upload file lên Cloudinary ──
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", publicId,
                            "resource_type", "image",
                            "overwrite", false
                    )
            );

            // ── Đọc kết quả từ Cloudinary ──
            String resultPublicId = (String) uploadResult.get("public_id");
            String version = String.valueOf(uploadResult.get("version"));
            String format = (String) uploadResult.get("format");
            int width = ((Number) uploadResult.get("width")).intValue();
            int height = ((Number) uploadResult.get("height")).intValue();

            // ── Lấy cloudName từ config ──
            String cloudName = (String) cloudinary.config.cloudName;

            // ── Tạo 3 URLs ──
            String baseUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/v" + version + "/" + resultPublicId + "." + format;
            String webUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_1920/v" + version + "/" + resultPublicId + "." + format;
            String thumbUrl = "https://res.cloudinary.com/" + cloudName
                    + "/image/upload/c_limit,w_320/v" + version + "/" + resultPublicId + "." + format;

            return new UploadResult(baseUrl, webUrl, thumbUrl, width, height, resultPublicId);

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload layer image to Cloudinary", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  MERGE METHODS (dành cho composite layers → final image)
    // ════════════════════════════════════════════════════════════════

    /**
     * ── uploadPageMerge ──
     * Upload ảnh đã merge (composite layers) lên Cloudinary.
     * Được PageService.mergeLayers() gọi sau khi composite xong.
     *
     * 📌 Khác với uploadPage (nhận MultipartFile từ frontend):
     *    uploadPageMerge nhận BufferedImage — ảnh đã được xử lý trong Java
     *    (composite layers bằng Graphics2D + AlphaComposite).
     *
     * 📌 Cấu trúc thư mục:
     *    manga_studio/chapters/ch{chapterId}/pages/p{pageId}/final.png
     *
     * @param image      BufferedImage đã composite xong (Graphics2D)
     * @param chapterId  ID của chapter
     * @param pageId     ID của page — không đổi, tránh ảnh hưởng khi reorder
     * @return UploadPageMergeResult chứa URLs
     */
    public UploadPageMergeResult uploadPageMerge(BufferedImage image,
                                                  Long chapterId, Long pageId) {
        try {
            String folder = "manga_studio/chapters/ch" + chapterId
                    + "/pages/p" + pageId;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            Map<?, ?> result = cloudinary.uploader().upload(
                    imageBytes,
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", "final",
                            "resource_type", "image",
                            "overwrite", true
                    )
            );

            String secureUrl = (String) result.get("secure_url");
            String cloudName = (String) cloudinary.config.cloudName;
            String resultPublicId = (String) result.get("public_id");
            String version = String.valueOf(result.get("version"));
            String format = (String) result.get("format");
            if (format == null) format = "png";

            System.out.println("[Cloudinary] uploadPageMerge — secure_url: " + secureUrl
                    + " | public_id: " + resultPublicId + " | version: " + version + " | format: " + format);

            String webUrl = secureUrl.replaceFirst("/image/upload/",
                    "/image/upload/c_limit,w_1920/");
            String thumbUrl = secureUrl.replaceFirst("/image/upload/",
                    "/image/upload/c_limit,w_320/");

            return new UploadPageMergeResult(secureUrl, webUrl, thumbUrl);

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload merged image to Cloudinary", e);
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

    @Value
    public static class UploadPageMergeResult {
        String finalImageUrl;
        String webImageUrl;
        String thumbnailUrl;
    }
}
