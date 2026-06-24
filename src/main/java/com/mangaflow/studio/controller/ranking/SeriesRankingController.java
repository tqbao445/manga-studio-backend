package com.mangaflow.studio.controller.ranking;

import com.mangaflow.studio.dto.ranking.response.AtRiskSeriesResponse;
import com.mangaflow.studio.dto.ranking.response.ImportResultResponse;
import com.mangaflow.studio.dto.ranking.response.RankingEntryResponse;
import com.mangaflow.studio.service.ranking.SeriesRankingService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ── SeriesRankingController ──
 * Controller duy nhất cho WEEKLY + MONTHLY ranking.
 *
 * Gồm 6 endpoints:
 * - Export (download Excel form)
 * - Import (upload Excel đã điền → tự tính ranking)
 * - Get ranking (lấy kết quả)
 */
@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "API xếp hạng series — WEEKLY và MONTHLY")
public class SeriesRankingController {

    private final SeriesRankingService rankingService;

    // ========================================================================
    // WEEKLY
    // ========================================================================

    @Operation(summary = "Export weekly scoring form",
            description = "Tải file Excel danh sách chapter PUBLISHED trong tuần, "
                    + "chỉ gồm series WEEKLY. EB điền Votes + AvgScore rồi import lại.")
    @GetMapping("/weekly/export")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<Resource> exportWeekly(
            @Parameter(description = "Week label (yyyy-'W'ww), VD: 2026-W25")
            @RequestParam String week) {

        byte[] excelBytes = rankingService.exportWeekly(week);
        ByteArrayResource resource = new ByteArrayResource(excelBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=weekly-scoring-" + week + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @Operation(summary = "Import weekly scoring file",
            description = "Upload file Excel đã điền Votes + AvgScore. "
                    + "Hệ thống tự động aggregate, tính score, gán rank cho tuần đó.")
    @PostMapping(value = "/weekly/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ImportResultResponse> importWeekly(
            @Parameter(description = "File Excel .xlsx đã điền Votes + AvgScore")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Week label (yyyy-'W'ww), VD: 2026-W25")
            @RequestParam String week) {
        return ResponseEntity.ok(rankingService.importWeekly(file, week));
    }

    @Operation(summary = "Get weekly ranking",
            description = "Trả về bảng xếp hạng tuần, sort theo rank.")
    @GetMapping("/weekly")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR', 'MANGAKA', 'TANTOU_EDITOR')")
    public ResponseEntity<List<RankingEntryResponse>> getWeeklyRanking(
            @Parameter(description = "Week label (yyyy-'W'ww), VD: 2026-W25")
            @RequestParam String week) {
        return ResponseEntity.ok(rankingService.getWeeklyRanking(week));
    }

    // ========================================================================
    // MONTHLY
    // ========================================================================

    @Operation(summary = "Export monthly scoring form",
            description = "Tải file Excel danh sách chapter PUBLISHED trong tháng, "
                    + "chỉ gồm series MONTHLY. EB điền Votes + AvgScore rồi import lại.")
    @GetMapping("/monthly/export")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<Resource> exportMonthly(
            @Parameter(description = "Month (yyyy-MM), VD: 2026-06")
            @RequestParam String month) {

        byte[] excelBytes = rankingService.exportMonthly(month);
        ByteArrayResource resource = new ByteArrayResource(excelBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=monthly-scoring-" + month + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @Operation(summary = "Import monthly scoring file",
            description = "Upload file Excel đã điền Votes + AvgScore. "
                    + "Hệ thống tự động aggregate, tính score, gán rank cho tháng đó.")
    @PostMapping(value = "/monthly/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<ImportResultResponse> importMonthly(
            @Parameter(description = "File Excel .xlsx đã điền Votes + AvgScore")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Month (yyyy-MM), VD: 2026-06")
            @RequestParam String month) {
        return ResponseEntity.ok(rankingService.importMonthly(file, month));
    }

    @Operation(summary = "Get monthly ranking",
            description = "Trả về bảng xếp hạng tháng, sort theo rank.")
    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR', 'MANGAKA', 'TANTOU_EDITOR')")
    public ResponseEntity<List<RankingEntryResponse>> getMonthlyRanking(
            @Parameter(description = "Month (yyyy-MM), VD: 2026-06")
            @RequestParam String month) {
        return ResponseEntity.ok(rankingService.getMonthlyRanking(month));
    }

    // ========================================================================
    // 🚨 AT-RISK — DANH SÁCH SERIES BỊ CẢNH BÁO
    // ========================================================================

    @Operation(summary = "Get at-risk series",
            description = "Trả về danh sách series đang AT_RISK kèm lịch sử ranking. "
                    + "Chỉ EDITORIAL_BOARD và CHIEF_EDITOR mới xem được.")
    @GetMapping("/at-risk")
    @PreAuthorize("hasAnyRole('EDITORIAL_BOARD', 'CHIEF_EDITOR')")
    public ResponseEntity<List<AtRiskSeriesResponse>> getAtRiskSeries() {
        return ResponseEntity.ok(rankingService.getAtRiskSeries());
    }
}
