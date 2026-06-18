package com.mangaflow.studio.controller.ranking;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.metric.response.SeriesMetricResponse;
import com.mangaflow.studio.dto.ranking.request.CancelDecisionRequest;
import com.mangaflow.studio.dto.ranking.response.ImportResultResponse;
import com.mangaflow.studio.dto.ranking.response.RankingEntryResponse;
import com.mangaflow.studio.service.metric.SeriesMetricService;
import com.mangaflow.studio.service.ranking.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 🎮 RankingController - API端点 quản lý xếp hạng series và import dữ liệu bình chọn.
 * <p>
 * Đây là tầng Controller (đầu vào REST API), tiếp nhận request từ client
 * và chuyển tiếp xuống tầng Service để xử lý business logic.
 * <p>
 * Các API:
     * - POST /api/ranking/import                                → Import file Excel metrics SERIES-LEVEL (chỉ EDITORIAL_BOARD/CHIEF_EDITOR)
     * - POST /api/ranking/import-chapters?month=YYYY-MM         → Import file Excel metrics CHAPTER-LEVEL + tự động aggregate (chỉ EDITORIAL_BOARD/CHIEF_EDITOR)
     * - GET  /api/ranking/export?month=YYYY-MM                  → Export file Excel form chấm điểm 2 sheets (chỉ EDITORIAL_BOARD/CHIEF_EDITOR)
 * - GET  /api/ranking                                       → Lấy bảng xếp hạng
 * - GET  /api/ranking/tiers                                 → Lấy ranking nhóm theo tier S/A/B/C/D
 * - POST /api/ranking/series/{id}/cancel-decision            → Chief quyết định giữ/hủy series tier D (chỉ CHIEF_EDITOR)
 * - GET  /api/ranking/series/{seriesId}/metrics             → Lịch sử metric của 1 series
 */
@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "API quản lý xếp hạng series và import dữ liệu bình chọn")
public class RankingController {

    // Service xử lý import Excel và lưu metrics
    private final SeriesMetricService seriesMetricService;

    // Service tính toán và lấy bảng xếp hạng
    private final RankingService rankingService;

    // ========================================================================
    // 📥 1. IMPORT DỮ LIỆU BÌNH CHỌN HÀNG THÁNG (POST)
    // ========================================================================

    /**
     * Import file Excel chứa dữ liệu bình chọn của độc giả.
     * <p>
     * 📋 Yêu cầu file .xlsx với 5 cột: SeriesId | Title | Month | TotalVotes | AvgScore
     * 🔐 Chỉ EDITORIAL_BOARD hoặc CHIEF_EDITOR mới có quyền gọi
     * ⚙️ Sau import, hệ thống tự động tính lại bảng xếp hạng
     *
     * @param file File Excel được upload
     * @return ImportResultResponse (tổng số dòng, thành công, danh sách lỗi)
     */
    @Operation(summary = "Import file Excel dữ liệu bình chọn",
               description = "Editorial Board upload file .xlsx chứa dữ liệu votes và avgScore của độc giả. "
                           + "Hệ thống tự động import vào DB, tính composite score và cập nhật bảng xếp hạng. "
                           + "Cột yêu cầu: SeriesId, Title, Month, TotalVotes, AvgScore.")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ImportResultResponse> importExcel(
            /*
             * @Parameter giúp Swagger UI hiểu rõ đây là file upload field.
             * Springdoc tự động nhận diện kiểu MultipartFile và hiển thị nút "Choose File".
             *
             * - consumes = MediaType.MULTIPART_FORM_DATA_VALUE: báo cho Spring biết
             *   endpoint này nhận dữ liệu multipart (form-data), nếu thiếu sẽ báo lỗi
             *   "Current request is not a multipart request"
             */
            @Parameter(description = "File Excel .xlsx (cột: SeriesId, Title, Month, TotalVotes, AvgScore)")
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(seriesMetricService.importExcel(file));
    }

    // ========================================================================
    // 📥 1B. IMPORT DỮ LIỆU BÌNH CHỌN CHAPTER-LEVEL (POST)
    // ========================================================================

    /**
     * Import file Excel chứa dữ liệu bình chọn CHAPTER-LEVEL.
     * <p>
     * 📋 Yêu cầu file .xlsx với 6 cột: SeriesId | SeriesTitle | ChapterNo | Title | Votes | AvgScore
     * 🔐 Chỉ EDITORIAL_BOARD hoặc CHIEF_EDITOR mới có quyền gọi
     * ⚙️ Hệ thống tự động:
     *    1. Parse từng chapter → lưu ChapterMetric
     *    2. Gom theo SeriesId → tính totalVotes = SUM(votes), avgScore = AVG(avgScore)
     *    3. Tính compositeScore = totalVotes * 0.6 + (totalVotes * avgScore / 10) * 0.4
     *    4. Upsert SeriesMetric và chạy RankingService.calculateRankings()
     *
     * @param file  File Excel được upload (.xlsx)
     * @param month Tháng của dữ liệu (YYYY-MM), truyền qua query param
     * @return ImportResultResponse (tổng số dòng, thành công, danh sách lỗi)
     */
    @Operation(summary = "Import file Excel dữ liệu bình chọn chapter-level",
            description = "Editorial Board upload file .xlsx chứa dữ liệu votes và avgScore "
                    + "theo từng chapter. Hệ thống tự động aggregate lên series-level, "
                    + "tính composite score và cập nhật bảng xếp hạng. "
                    + "Cột yêu cầu: SeriesId, SeriesTitle, ChapterNo, Title, Votes, AvgScore.")
    @PostMapping(value = "/import-chapters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ImportResultResponse> importChapters(
            @Parameter(description = "File Excel .xlsx (cột: SeriesId, SeriesTitle, ChapterNo, Title, Votes, AvgScore)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Tháng của dữ liệu (YYYY-MM)")
            @RequestParam String month) {
        return ResponseEntity.ok(seriesMetricService.importChapterExcel(file, month));
    }

    // ========================================================================
    // 📤 2. EXPORT FILE EXCEL FORM CHẤM ĐIỂM (GET)
    // ========================================================================

    /**
     * Export file Excel form chấm điểm hàng tháng cho Chief Board (2 SHEETS).
     * <p>
     * 📋 Sheet 1 "Chapter Scoring":
     *   Chi tiết từng chapter theo từng series, dựa trên PublicationSchedule ACTIVE:
     *   - WEEKLY → 4 chapters/tháng
     *   - MONTHLY / không có schedule → 1 chapter/tháng
     *   Các cột: SeriesId | SeriesTitle | ChapterNo | Title | Votes | AvgScore
     *   Votes và AvgScore để trống — Chief điền sau đó upload qua POST /api/ranking/import-chapters
     * <p>
     * 📋 Sheet 2 "Series Ranking":
     *   Tổng hợp kết quả (TotalVotes, AvgScore, CompositeScore, Tier, Rank)
     *   kèm Prev_Tier và Prev_CompositeScore để so sánh với tháng trước.
     * <p>
     * 🔐 Chỉ EDITORIAL_BOARD hoặc CHIEF_EDITOR mới có quyền gọi.
     *
     * @param month Tháng cần tạo form (định dạng "YYYY-MM", VD: "2026-06")
     * @return File .xlsx (2 sheets) dưới dạng attachment (download)
     */
    @Operation(summary = "Export file Excel form chấm điểm 2 sheets",
            description = "Tải file Excel gồm 2 sheets: "
                    + "(1) Chapter Scoring — Chief điền votes/avgScore từng chapter, "
                    + "(2) Series Ranking — kết quả tổng hợp + so sánh tháng trước. "
                    + "Sau đó upload Sheet 1 qua API POST /api/ranking/import-chapters.")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<Resource> exportExcel(
            @Parameter(description = "Tháng cần tạo form (YYYY-MM)")
            @RequestParam String month) {

        // Gọi service tạo file Excel → nhận mảng byte
        byte[] excelBytes = seriesMetricService.exportScoringForm(month);

        // Đóng gói thành Resource để Spring trả về
        ByteArrayResource resource = new ByteArrayResource(excelBytes);

        // Trả về file dưới dạng attachment (trình duyệt tự động download)
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=scoring-form-" + month + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    // ========================================================================
    // 📊 3. LẤY BẢNG XẾP HẠNG (GET)
    // ========================================================================

    /**
     * Lấy danh sách tất cả series đã có thứ hạng, sắp xếp theo rank tăng dần.
     * <p>
     * Kết quả trả về mỗi series gồm: rank, tier, tên, thể loại, tác giả, trạng thái...
     *
     * @return Danh sách RankingEntryResponse đã sắp xếp
     */
    @Operation(summary = "Xem bảng xếp hạng series",
               description = "Trả về danh sách tất cả series đã có thứ hạng, sorted theo rank. "
                           + "Có thể lọc theo tháng (nếu không truyền month thì dùng current rank trên Series).")
    @GetMapping
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<List<RankingEntryResponse>> getRankings(
            @Parameter(description = "Tháng cần xem ranking (YYYY-MM), optional")
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(rankingService.getRankings(month));
    }

    /**
     * Lấy bảng xếp hạng nhưng nhóm theo tier (S/A/B/C/D).
     * <p>
     * Dùng TreeMap để đảm bảo thứ tự: S → A → B → C → D
     *
     * @return Map với key là tier, value là danh sách series trong tier đó
     */
    @Operation(summary = "Xem ranking theo tier",
               description = "Trả về bảng xếp hạng nhóm theo tier S/A/B/C/D.")
    @GetMapping("/tiers")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<Map<String, List<RankingEntryResponse>>> getRankingsByTier() {
        return ResponseEntity.ok(rankingService.getRankingsByTier());
    }

    // ========================================================================
    // ⚖️ 4. CHIEF EDITOR QUYẾT ĐỊNH GIỮ/HỦY SERIES (POST)
    // ========================================================================

    /**
     * Xử lý quyết định của Chief Editor khi series ở tier D >= 3 tháng.
     * <p>
     * Chief Editor gửi request với decision = "KEEP" (giữ lại) hoặc "CANCEL" (hủy).
     * - KEEP: Reset warning, chuyển về ONGOING
     * - CANCEL: Chuyển sang CANCELLED
     * <p>
     * 🔐 Chỉ CHIEF_EDITOR mới có quyền gọi (quyết định cuối cùng).
     *
     * @param seriesId ID của series cần xử lý
     * @param request  Body chứa decision ("KEEP" hoặc "CANCEL")
     * @param user     Thông tin user từ JWT (lấy ai là người quyết định)
     */
    @Operation(summary = "Chief Editor quyết định giữ/hủy series tier D",
            description = "Khi series ở tier D >= 3 tháng, Chief Editor gọi API này "
                    + "để quyết định KEEP (giữ lại) hoặc CANCEL (hủy).")
    @PostMapping("/series/{seriesId}/cancel-decision")
    @PreAuthorize("hasRole('CHIEF_EDITOR')")
    public ResponseEntity<Void> cancelDecision(
            @Parameter(description = "ID của series cần xử lý")
            @PathVariable Long seriesId,
            @RequestBody CancelDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {

        rankingService.handleCancelDecision(seriesId, request.getDecision(), user.getUser());
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // 📈 5. LỊCH SỬ METRIC CỦA 1 SERIES (GET)
    // ========================================================================

    /**
     * Lấy lịch sử metrics (theo tháng) của một series cụ thể.
     * Kết quả sắp xếp tháng mới nhất trước.
     *
     * @param seriesId ID của series cần xem
     * @return Danh sách SeriesMetricResponse (lịch sử các tháng)
     */
    @Operation(summary = "Xem lịch sử metrics của 1 series",
               description = "Trả về tất cả bản ghi SeriesMetric theo tháng của series, mới nhất trước.")
    @GetMapping("/series/{seriesId}/metrics")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<List<SeriesMetricResponse>> getSeriesMetrics(
            @PathVariable Long seriesId) {
        return ResponseEntity.ok(seriesMetricService.getMetricsBySeries(seriesId));
    }

    // ========================================================================
    // 📊 6. LỊCH SỬ RANKING THEO THÁNG (GET)
    // ========================================================================

    @Operation(summary = "Xem lịch sử ranking tất cả tháng",
               description = "Trả về tất cả SeriesMetric gộp theo tháng, dùng cho History modal.")
    @GetMapping("/metrics/history")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<List<SeriesMetricResponse>> getAllMetrics() {
        return ResponseEntity.ok(seriesMetricService.getAllMetrics());
    }
}
