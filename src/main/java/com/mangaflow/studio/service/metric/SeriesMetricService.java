package com.mangaflow.studio.service.metric;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.metric.response.SeriesMetricResponse;
import com.mangaflow.studio.dto.ranking.response.ImportResultResponse;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.metric.ChapterMetric;
import com.mangaflow.studio.model.metric.SeriesMetric;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.metric.ChapterMetricRepository;
import com.mangaflow.studio.repository.metric.SeriesMetricRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 📥 SeriesMetricService - Service xử lý dữ liệu metrics (chỉ số) của series.
 * <p>
 * Nhiệm vụ chính:
 * 1️⃣ Export file Excel 2 sheets (Chapter Scoring + Series Ranking)
 * 2️⃣ Import file chapter-level (tự động aggregate → series → ranking)
 * 3️⃣ Import file series-level (cũ, giữ nguyên)
 * 4️⃣ Lấy lịch sử metrics của series
 * <p>
 * 📂 File Excel gồm 2 sheets:
 * - Sheet 1 "Chapter Scoring": Chief điền votes/avgScore từng chapter
 * - Sheet 2 "Series Ranking": Kết quả tổng hợp + so sánh tháng trước
 */
@Service
@RequiredArgsConstructor
public class SeriesMetricService {

    private final SeriesMetricRepository metricRepository;
    private final SeriesRepository seriesRepository;
    private final RankingService rankingService;
    private final ChapterMetricRepository chapterMetricRepository;
    private final ChapterRepository chapterRepository;

    // ========================================================================
    // 🚀 IMPORT (cũ): File Excel dữ liệu bình chọn SERIES-LEVEL
    // ========================================================================

    /**
     * Import file Excel chứa dữ liệu metrics của độc giả (SERIES-LEVEL).
     * <p>
     * File cũ: SeriesId | Title | Month | TotalVotes | AvgScore
     * <p>
     * 🔄 Quy trình: đọc file → upsert SeriesMetric → tính ranking
     *
     * @param file File .xlsx được upload lên
     * @return ImportResultResponse chứa thống kê kết quả import
     */
    @Transactional
    public ImportResultResponse importExcel(MultipartFile file) {
        List<ImportResultResponse.ErrorRow> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        String importedMonth = null;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is empty");
            }

            Row headerRow = sheet.getRow(0);
            validateHeaders(headerRow);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                totalRows++;
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    processRow(row);

                    if (importedMonth == null) {
                        importedMonth = getCellStringValue(row.getCell(2));
                        validateMonthIsPast(importedMonth);
                    }
                    successRows++;
                } catch (Exception e) {
                    errors.add(ImportResultResponse.ErrorRow.builder()
                            .rowNumber(i + 1)
                            .message(e.getMessage())
                            .build());
                }
            }

            if (importedMonth != null && successRows > 0) {
                rankingService.calculateRankings(importedMonth);
            }

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Failed to read Excel file: " + e.getMessage());
        }

        return ImportResultResponse.builder()
                .totalRows(totalRows)
                .successRows(successRows)
                .errorRows(errors)
                .build();
    }

    // ========================================================================
    // 🚀 IMPORT (mới): File Excel dữ liệu bình chọn CHAPTER-LEVEL
    // ========================================================================

    /**
     * 📥 IMPORT FILE EXCEL CHAPTER-LEVEL.
     * <p>
     * File này do Chief Board tải về từ export, điền votes/avgScore từng chapter,
     * sau đó upload lên đây. Hệ thống tự động:
     * <p>
     * 1️⃣ Parse từng dòng chapter → lưu ChapterMetric
     * 2️⃣ Gom theo SeriesId → tính totalVotes = SUM(votes), avgScore = AVG(avgScore)
     * 3️⃣ Tính compositeScore = totalVotes * 0.6 + (totalVotes * avgScore / 10) * 0.4
     * 4️⃣ Upsert SeriesMetric
     * 5️⃣ Chạy RankingService.calculateRankings()
     * <p>
     * 📋 Sheet 1 yêu cầu các cột: SeriesId | SeriesTitle | ChapterNo | Title | Votes | AvgScore
     *
     * @param file  File .xlsx được upload lên (đã điền votes/avgScore)
     * @param month Tháng của dữ liệu (YYYY-MM), truyền từ URL param
     * @return ImportResultResponse chứa thống kê kết quả import
     */
    @Transactional
    public ImportResultResponse importChapterExcel(MultipartFile file, String month) {

        // ── Validate month ──
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid month format (expected YYYY-MM): " + month);
        }
        validateMonthIsPast(month);

        List<ImportResultResponse.ErrorRow> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        // Map lưu aggregate theo SeriesId: SeriesId → (series, tổng votes, tổng avgScore, số chapter)
        // Dùng LinkedHashMap để giữ thứ tự insert (theo thứ tự xuất hiện trong file)
        Map<Long, ChapterAggregate> aggregateMap = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            // ── Lấy Sheet 1 (Chapter Scoring) ──
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is empty");
            }

            // ── Validate header ──
            validateChapterHeaders(sheet.getRow(0));

            // ── Duyệt từng dòng dữ liệu (bắt đầu từ dòng 1) ──
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                totalRows++;
                Row row = sheet.getRow(i);
                if (row == null) continue;  // Bỏ qua dòng trống

                // Lưu số dòng thực tế trong Excel (1-indexed) để dùng trong lambda
                // (biến i không effectively final vì là loop variable)
                int excelRowNum = i + 1;

                try {
                    // Đọc dữ liệu từ các cột
                    Long seriesId = getCellNumericValue(row.getCell(0));   // Cột A: SeriesId
                    // Cột B: SeriesTitle (chỉ để đọc, không validate)
                    Integer chapterNo = getCellNumericValue(row.getCell(2)) != null
                            ? getCellNumericValue(row.getCell(2)).intValue() : null;  // Cột C: ChapterNo
                    // Cột D: ChapterTitle (chỉ để đọc)
                    Long votes = getCellNumericValue(row.getCell(4));       // Cột E: Votes
                    Double avgScore = getCellDoubleValue(row.getCell(5));   // Cột F: AvgScore

                    // ── Validate dữ liệu ──
                    if (seriesId == null) {
                        throw new IllegalArgumentException("SeriesId is required at row " + excelRowNum);
                    }
                    if (votes == null) {
                        throw new IllegalArgumentException("Votes is required at row " + excelRowNum);
                    }
                    if (votes < 0) {
                        throw new IllegalArgumentException("Votes cannot be negative at row " + excelRowNum);
                    }
                    if (avgScore == null || avgScore < 0 || avgScore > 10) {
                        throw new IllegalArgumentException(
                                "AvgScore must be between 0 and 10 at row " + excelRowNum);
                    }

                    // Kiểm tra series có tồn tại trong DB không
                    Series series = seriesRepository.findById(seriesId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Series not found with id: " + seriesId + " at row " + excelRowNum));

                    // ── Lưu ChapterMetric (nếu chapter PUBLISHED trong DB) ──
                    // Chỉ lưu ChapterMetric cho chapter đã PUBLISHED — chapter DRAFT/APPROVED không được tính
                    // Nếu chapter chưa có trong DB thì bỏ qua ChapterMetric,
                    // nhưng vẫn dùng dữ liệu Excel để aggregate series-level.
                    if (chapterNo != null) {
                        chapterRepository.findBySeriesIdAndChapterNumber(seriesId, chapterNo)
                                .filter(ch -> ch.getStatus() == ChapterStatus.PUBLISHED)
                                .ifPresent(chapter -> saveChapterMetric(chapter, month, votes, avgScore));
                    }

                    // ── Accumulate vào aggregate map ──
                    // Nếu seriesId đã có trong map → cộng dồn votes và avgScore
                    // Nếu chưa có → tạo mới
                    aggregateMap.merge(seriesId, new ChapterAggregate(series, votes, avgScore, 1),
                            (existing, incoming) -> {
                                existing.totalVotes += incoming.totalVotes;
                                existing.totalAvgScore += incoming.totalAvgScore;
                                existing.chapterCount += incoming.chapterCount;
                                return existing;
                            });

                    successRows++;

                } catch (Exception e) {
                    errors.add(ImportResultResponse.ErrorRow.builder()
                            .rowNumber(excelRowNum)
                            .message(e.getMessage())
                            .build());
                }
            }

            // ── Sau khi parse hết → aggregate và lưu SeriesMetric ──
            if (!aggregateMap.isEmpty()) {
                for (ChapterAggregate agg : aggregateMap.values()) {
                    // Tính totalVotes = tổng votes của tất cả chapters
                    long totalVotes = agg.totalVotes;

                    // Tính avgScore = trung bình cộng avgScore của các chapters
                    double avgScore = agg.totalAvgScore / agg.chapterCount;

                    // 🔢 Tính compositeScore:
                    // Công thức: 60% số lượng phiếu + 40% điểm chất lượng
                    double compositeScore = totalVotes * 0.6
                            + (totalVotes * avgScore / 10.0) * 0.4;

                    // Upsert SeriesMetric (update nếu đã có, insert nếu chưa)
                    Optional<SeriesMetric> existing =
                            metricRepository.findBySeriesIdAndMonth(agg.series.getId(), month);
                    SeriesMetric metric;
                    if (existing.isPresent()) {
                        metric = existing.get();
                        metric.setTotalVotes(totalVotes);
                        metric.setAvgScore(avgScore);
                        metric.setCompositeScore(compositeScore);
                    } else {
                        metric = SeriesMetric.builder()
                                .series(agg.series)
                                .month(month)
                                .totalVotes(totalVotes)
                                .avgScore(avgScore)
                                .compositeScore(compositeScore)
                                .build();
                    }
                    metricRepository.save(metric);
                }

                // ⭐ Kích hoạt tính toán lại bảng xếp hạng
                rankingService.calculateRankings(month);
            }

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Failed to read Excel file: " + e.getMessage());
        }

        return ImportResultResponse.builder()
                .totalRows(totalRows)
                .successRows(successRows)
                .errorRows(errors)
                .build();
    }

    /**
     * Lưu ChapterMetric (upsert).
     * Nếu đã có metric cho chapter + tháng này → update, chưa có → insert.
     */
    private void saveChapterMetric(Chapter chapter, String month, Long votes, Double avgScore) {
        Optional<ChapterMetric> existing =
                chapterMetricRepository.findByChapterIdAndMonth(chapter.getId(), month);
        ChapterMetric metric;
        if (existing.isPresent()) {
            metric = existing.get();
            metric.setVotes(votes);
            metric.setAvgScore(avgScore);
        } else {
            metric = ChapterMetric.builder()
                    .chapter(chapter)
                    .month(month)
                    .votes(votes)
                    .avgScore(avgScore)
                    .build();
        }
        chapterMetricRepository.save(metric);
    }

    // ========================================================================
    // 🔍 KIỂM TRA HEADER FILE EXCEL (series-level cũ)
    // ========================================================================

    /**
     * Kiểm tra dòng header của Excel (format cũ: series-level).
     * Yêu cầu 5 cột: SeriesId, Title, Month, TotalVotes, AvgScore.
     */
    private void validateHeaders(Row headerRow) {
        if (headerRow == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Excel file has no header row");
        }
        String[] expectedHeaders = {"SeriesId", "Title", "Month", "TotalVotes", "AvgScore"};
        for (int i = 0; i < expectedHeaders.length; i++) {
            String actual = getCellStringValue(headerRow.getCell(i));
            if (actual == null || !actual.trim().equalsIgnoreCase(expectedHeaders[i])) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Invalid header at column " + i + ": expected '" + expectedHeaders[i]
                                + "' but found '" + actual + "'");
            }
        }
    }

    // ========================================================================
    // 🔍 KIỂM TRA HEADER FILE EXCEL (chapter-level mới)
    // ========================================================================

    /**
     * Kiểm tra dòng header của Excel (format mới: chapter-level).
     * Yêu cầu 6 cột: SeriesId, SeriesTitle, ChapterNo, Title, Votes, AvgScore.
     */
    private void validateChapterHeaders(Row headerRow) {
        if (headerRow == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Excel file has no header row");
        }
        String[] expectedHeaders = {"SeriesId", "SeriesTitle", "ChapterNo", "Title", "Votes", "AvgScore"};
        for (int i = 0; i < expectedHeaders.length; i++) {
            String actual = getCellStringValue(headerRow.getCell(i));
            if (actual == null || !actual.trim().equalsIgnoreCase(expectedHeaders[i])) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Invalid header at column " + i + ": expected '" + expectedHeaders[i]
                                + "' but found '" + actual + "'");
            }
        }
    }

    // ========================================================================
    // ⚙️ XỬ LÝ 1 DÒNG DỮ LIỆU (series-level cũ)
    // ========================================================================

    /**
     * Xử lý một dòng dữ liệu trong Excel (format cũ: series-level).
     * Đọc SeriesId, Month, TotalVotes, AvgScore → tính compositeScore → upsert SeriesMetric.
     */
    private void processRow(Row row) {
        Long seriesId = getCellNumericValue(row.getCell(0));
        if (seriesId == null) {
            throw new IllegalArgumentException("SeriesId is required");
        }

        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Series not found with id: " + seriesId));

        String month = getCellStringValue(row.getCell(2));
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Invalid Month format (expected YYYY-MM): " + month);
        }

        Long totalVotes = getCellNumericValue(row.getCell(3));
        if (totalVotes == null) {
            throw new IllegalArgumentException("TotalVotes is required");
        }

        Double avgScore = getCellDoubleValue(row.getCell(4));
        if (avgScore == null || avgScore < 0 || avgScore > 10) {
            throw new IllegalArgumentException("AvgScore must be between 0 and 10");
        }

        double compositeScore = totalVotes * 0.6 + (totalVotes * avgScore / 10.0) * 0.4;

        Optional<SeriesMetric> existing = metricRepository.findBySeriesIdAndMonth(seriesId, month);
        SeriesMetric metric;
        if (existing.isPresent()) {
            metric = existing.get();
            metric.setTotalVotes(totalVotes);
            metric.setAvgScore(avgScore);
            metric.setCompositeScore(compositeScore);
        } else {
            metric = SeriesMetric.builder()
                    .series(series)
                    .month(month)
                    .totalVotes(totalVotes)
                    .avgScore(avgScore)
                    .compositeScore(compositeScore)
                    .build();
        }
        metricRepository.save(metric);
    }

    // ========================================================================
    // ⏰ KIỂM TRA THÁNG IMPORT — CHỈ CHO PHÉP IMPORT THÁNG ĐÃ KẾT THÚC
    // ========================================================================

    /**
     * Kiểm tra tháng import có phải là tháng đã kết thúc hay không.
     * Chỉ cho phép import dữ liệu của tháng TRƯỚC tháng hiện tại.
     * <p>
     * Lý do: Ngăn Chief Board nhập điểm giữa tháng khi chưa đủ dữ liệu đánh giá.
     *
     * @param month Tháng cần kiểm tra (định dạng "YYYY-MM")
     * @throws AppException nếu tháng import là tháng hiện tại hoặc tương lai
     */
    private void validateMonthIsPast(String month) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth importedMonth = YearMonth.parse(month);

        if (!importedMonth.isBefore(currentMonth)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot import data for current or future month. "
                            + "Imported: " + month
                            + ", Current: " + currentMonth
                            + ". Please wait until the month ends.");
        }
    }

    // ========================================================================
    // 📤 API EXPORT: Xuất file Excel form chấm điểm hàng tháng (2 SHEETS)
    // ========================================================================

    /**
     * 📤 XUẤT FILE EXCEL FORM CHẤM ĐIỂM CHO CHIEF BOARD (2 SHEETS).
     * <p>
     * 🎯 File này gồm 2 sheets:
     * <p>
     * ─── Sheet 1: "Chapter Scoring" ───
     *   Chief Board điền Votes và AvgScore cho từng chapter.
     *   Chỉ lấy chapter đã PUBLISHED trong tháng cần export (dựa trên publishDate).
     *   Nếu series có 0 chapter PUBLISHED trong tháng → không xuất hiện trên Sheet 1.
     *   Votes và AvgScore để trống.
     * <p>
     * ─── Sheet 2: "Series Ranking" ───
     *   Kết quả tổng hợp để Chief Board so sánh.
     *   Nếu đã import tháng này → show dữ liệu tháng này.
     *   Nếu chưa import → show dữ liệu tháng trước làm reference.
     *   Có cột Prev_Tier và Prev_CompositeScore để so sánh với tháng trước nữa.
     * <p>
     * 📌 CHỈ lấy các series đang phát hành (ONGOING + AT_RISK).
     *
     * @param month Tháng cần tạo form (định dạng "YYYY-MM")
     * @return byte[] nội dung file .xlsx
     */
    public byte[] exportScoringForm(String month) {

        // ── Validate month ──
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid month format (expected YYYY-MM): " + month);
        }

        // ── Lấy danh sách active series ──
        List<Series> activeSeries = seriesRepository.findByStatusIn(
                List.of(SeriesStatus.ONGOING, SeriesStatus.AT_RISK)
        );

        // Sắp xếp theo rank (nếu có)
        activeSeries.sort(Comparator.comparing(
                s -> s.getCurrentRank() != null ? s.getCurrentRank() : Integer.MAX_VALUE
        ));

        // ── Tính tháng trước (previous month) ──
        String prevMonth = getPreviousMonth(month);

        // ── Lấy dữ liệu metrics cho Sheet 2 ──
        // currentData: metrics của tháng này (có thể null nếu chưa import)
        List<SeriesMetric> currentMetrics = metricRepository.findByMonth(month);

        // previousData: metrics của tháng trước (để so sánh)
        List<SeriesMetric> previousMetrics = metricRepository.findByMonth(prevMonth);

        // Build lookup map: SeriesId → SeriesMetric
        Map<Long, SeriesMetric> currentMap = currentMetrics.stream()
                .collect(Collectors.toMap(m -> m.getSeries().getId(), m -> m));
        Map<Long, SeriesMetric> previousMap = previousMetrics.stream()
                .collect(Collectors.toMap(m -> m.getSeries().getId(), m -> m));

        try (Workbook workbook = new XSSFWorkbook()) {

            // ── Tạo font/styłe ──
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Font groupFont = workbook.createFont();
            groupFont.setBold(true);
            groupFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle groupStyle = workbook.createCellStyle();
            groupStyle.setFont(groupFont);
            groupStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            groupStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // =================================================================
            // SHEET 1: "Chapter Scoring" — Chief điền votes/avgScore từng chapter
            // =================================================================

            Sheet chapterSheet = workbook.createSheet("Chapter Scoring");

            // Header Sheet 1
            String[] chapterHeaders = {"SeriesId", "SeriesTitle", "ChapterNo", "Title", "Votes", "AvgScore"};
            Row chapterHeaderRow = chapterSheet.createRow(0);
            for (int i = 0; i < chapterHeaders.length; i++) {
                Cell cell = chapterHeaderRow.createCell(i);
                cell.setCellValue(chapterHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int chapterRowIdx = 1;

            // Duyệt từng series để tạo chapter rows
            // ── Lấy chapter PUBLISHED trong tháng → số dòng = số chapter thật ──
            YearMonth ym = YearMonth.parse(month);
            LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = ym.atEndOfMonth().atTime(23, 59, 59);

            for (Series series : activeSeries) {

                // Query chapter PUBLISHED của series trong tháng này
                List<Chapter> publishedChapters = chapterRepository
                        .findBySeriesIdAndStatusAndPublishDateBetween(
                                series.getId(),
                                ChapterStatus.PUBLISHED,
                                monthStart,
                                monthEnd);

                if (publishedChapters.isEmpty()) {
                    // Không có chapter nào được publish trong tháng → skip khỏi Sheet 1
                    // Ranking sẽ tự tạo 0-score cho series này (xem RankingService.calculateRankings)
                    continue;
                }

                // ── Tạo 1 dòng cho mỗi chapter PUBLISHED ──
                for (Chapter ch : publishedChapters) {
                    Row row = chapterSheet.createRow(chapterRowIdx++);

                    // Cột 0: SeriesId
                    row.createCell(0).setCellValue(series.getId());
                    // Cột 1: SeriesTitle
                    row.createCell(1).setCellValue(series.getTitle() != null ? series.getTitle() : "");
                    // Cột 2: ChapterNo thật của chapter
                    row.createCell(2).setCellValue(ch.getChapterNumber());
                    // Cột 3: ChapterTitle thật
                    row.createCell(3).setCellValue(ch.getTitle() != null ? ch.getTitle() : "");
                    // Cột 4: Votes (để trống — Chief tự điền)
                    row.createCell(4).setCellValue("");
                    // Cột 5: AvgScore (để trống — Chief tự điền)
                    row.createCell(5).setCellValue("");
                }
            }

            // Auto-size columns cho Sheet 1
            for (int i = 0; i < chapterHeaders.length; i++) {
                chapterSheet.autoSizeColumn(i);
            }

            // =================================================================
            // SHEET 2: "Series Ranking" — Kết quả tổng hợp + so sánh
            // =================================================================

            Sheet rankingSheet = workbook.createSheet("Series Ranking");

            // Header Sheet 2: thêm Prev_Tier và Prev_CompositeScore để so sánh
            String[] rankingHeaders = {
                    "SeriesId", "Title", "Month", "TotalVotes", "AvgScore",
                    "CompositeScore", "Tier", "Rank",
                    "Prev_Tier", "Prev_CompositeScore"
            };
            Row rankingHeaderRow = rankingSheet.createRow(0);
            for (int i = 0; i < rankingHeaders.length; i++) {
                Cell cell = rankingHeaderRow.createCell(i);
                cell.setCellValue(rankingHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int rankingRowIdx = 1;

            // Duyệt từng series để tạo ranking rows
            for (Series series : activeSeries) {
                Row row = rankingSheet.createRow(rankingRowIdx++);

                // Lấy metric của tháng này (nếu có)
                SeriesMetric currentMetric = currentMap.get(series.getId());
                // Lấy metric của tháng trước (nếu có) — dùng cho cột Prev
                SeriesMetric prevMetric = previousMap.get(series.getId());

                // Nếu chưa có currentMetric → dùng previousMetric làm dữ liệu hiển thị
                SeriesMetric displayMetric = currentMetric != null ? currentMetric : prevMetric;
                // 💡 compareMetric = data tháng trước (dùng cho cột Prev_Tier, Prev_CompositeScore)
                // Luôn lấy prevMetric nếu có — KHÔNG phụ thuộc vào currentMetric
                // Để cho dù chưa import tháng này, user vẫn thấy Prev_Tier và Prev_CompositeScore
                SeriesMetric compareMetric = prevMetric;

                // Cột 0: SeriesId
                row.createCell(0).setCellValue(series.getId());
                // Cột 1: Title
                row.createCell(1).setCellValue(series.getTitle() != null ? series.getTitle() : "");
                // Cột 2: Month
                row.createCell(2).setCellValue(month);

                if (displayMetric != null) {
                    // Cột 3: TotalVotes
                    row.createCell(3).setCellValue(
                            displayMetric.getTotalVotes() != null ? displayMetric.getTotalVotes() : 0);
                    // Cột 4: AvgScore
                    row.createCell(4).setCellValue(
                            displayMetric.getAvgScore() != null ? displayMetric.getAvgScore() : 0.0);
                    // Cột 5: CompositeScore
                    row.createCell(5).setCellValue(
                            displayMetric.getCompositeScore() != null ? displayMetric.getCompositeScore() : 0.0);
                    // Cột 6: Tier (lấy từ Series entity, đã cập nhật sau ranking)
                    row.createCell(6).setCellValue(
                            series.getCurrentTier() != null ? series.getCurrentTier() : "");
                    // Cột 7: Rank
                    row.createCell(7).setCellValue(
                            series.getCurrentRank() != null ? series.getCurrentRank() : 0);
                } else {
                    // Chưa có dữ liệu gì → để trống
                    row.createCell(3).setCellValue("");
                    row.createCell(4).setCellValue("");
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue(
                            series.getCurrentTier() != null ? series.getCurrentTier() : "");
                    row.createCell(7).setCellValue(
                            series.getCurrentRank() != null ? series.getCurrentRank() : 0);
                }

                // Cột 8: Prev_Tier — tier tháng trước (lấy từ SeriesMetric đã lưu)
                // Cột 9: Prev_CompositeScore — compositeScore tháng trước
                if (compareMetric != null) {
                    // ✅ Đã lưu tier vào SeriesMetric (nhờ RankingService.calculateRankings())
                    row.createCell(8).setCellValue(
                            compareMetric.getTier() != null ? compareMetric.getTier() : "");
                    row.createCell(9).setCellValue(
                            compareMetric.getCompositeScore() != null
                                    ? compareMetric.getCompositeScore() : 0.0);
                } else {
                    row.createCell(8).setCellValue("");
                    row.createCell(9).setCellValue("");
                }
            }

            // Auto-size columns cho Sheet 2
            // ⚠️ setColumnWidth sau autoSizeColumn để đảm bảo column không bị quá hẹp
            // (khi tất cả cells trong 1 cột đều rỗng, autoSizeColumn sẽ co về 0 → cột biến mất)
            for (int i = 0; i < rankingHeaders.length; i++) {
                rankingSheet.autoSizeColumn(i);
                // Set minimum width = 10 ký tự (mỗi ký tự ~256 đơn vị)
                int minWidth = 10 * 256;
                if (rankingSheet.getColumnWidth(i) < minWidth) {
                    rankingSheet.setColumnWidth(i, minWidth);
                }
            }

            // ── Ghi workbook ra byte array ──
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to export Excel: " + e.getMessage());
        }
    }

    // ========================================================================
    // 🛠️ HÀM TIỆN ÍCH: TÍNH THÁNG TRƯỚC
    // ========================================================================

    /**
     * Tính tháng trước dựa trên tháng hiện tại.
     * VD: "2026-06" → "2026-05", "2026-01" → "2025-12"
     *
     * @param month Tháng hiện tại (YYYY-MM)
     * @return Tháng trước (YYYY-MM)
     */
    private String getPreviousMonth(String month) {
        YearMonth ym = YearMonth.parse(month);
        return ym.minusMonths(1).toString();
    }

    // ========================================================================
    // 🛠️ CÁC HÀM TIỆN ÍCH ĐỌC DỮ LIỆU TỪ CELL EXCEL
    // ========================================================================

    /**
     * Đọc giá trị kiểu String từ ô Excel.
     * Xử lý linh hoạt: dù ô là TEXT hay SỐ đều đọc được.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    /**
     * Đọc giá trị kiểu Long (số nguyên) từ ô Excel.
     * Xử lý cả ô dạng số và ô dạng text có chứa số.
     */
    private Long getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (long) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Long.parseLong(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Đọc giá trị kiểu Double (số thực) từ ô Excel.
     * Xử lý cả ô dạng số và ô dạng text.
     */
    private Double getCellDoubleValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    // ========================================================================
    // 📋 LỊCH SỬ METRIC CỦA 1 SERIES
    // ========================================================================

    /**
     * Lấy toàn bộ lịch sử metrics của 1 series (tất cả các tháng đã import).
     * Kết quả sắp xếp theo tháng mới nhất trước.
     *
     * @param seriesId ID của series cần xem
     * @return Danh sách SeriesMetricResponse (đã map từ Entity sang DTO)
     */
    @Transactional(readOnly = true)
    public List<SeriesMetricResponse> getMetricsBySeries(Long seriesId) {
        List<SeriesMetric> metrics = metricRepository.findBySeriesIdOrderByMonthDesc(seriesId);
        return metrics.stream()
                .map(m -> SeriesMetricResponse.builder()
                        .id(m.getId())
                        .seriesId(m.getSeries().getId())
                        .seriesTitle(m.getSeries().getTitle())
                        .month(m.getMonth())
                        .totalVotes(m.getTotalVotes())
                        .avgScore(m.getAvgScore())
                        .compositeScore(m.getCompositeScore())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SeriesMetricResponse> getHistoryByMonth(String month) {
        List<SeriesMetric> metrics = metricRepository.findByMonth(month);
        return metrics.stream()
                .map(m -> SeriesMetricResponse.builder()
                        .id(m.getId())
                        .seriesId(m.getSeries().getId())
                        .seriesTitle(m.getSeries().getTitle())
                        .month(m.getMonth())
                        .totalVotes(m.getTotalVotes())
                        .avgScore(m.getAvgScore())
                        .compositeScore(m.getCompositeScore())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SeriesMetricResponse> getAllMetrics() {
        List<SeriesMetric> metrics = metricRepository.findAll();
        return metrics.stream()
                .map(m -> SeriesMetricResponse.builder()
                        .id(m.getId())
                        .seriesId(m.getSeries().getId())
                        .seriesTitle(m.getSeries().getTitle())
                        .month(m.getMonth())
                        .totalVotes(m.getTotalVotes())
                        .avgScore(m.getAvgScore())
                        .compositeScore(m.getCompositeScore())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ========================================================================
    // 🏠 CLASS PHỤ: Lưu dữ liệu aggregate chapter → series
    // ========================================================================

    /**
     * ChapterAggregate - Class tạm dùng để aggregate dữ liệu chapter-level
     * lên series-level trong quá trình import.
     * <p>
     * Lưu: Series, tổng votes, tổng avgScore, số chapter đã gộp.
     * Dùng để tính: totalVotes = sum(votes), avgScore = sum(avgScore)/count
     */
    @lombok.AllArgsConstructor
    private static class ChapterAggregate {
        Series series;           // Series cần aggregate
        long totalVotes;         // Tổng votes của tất cả chapters
        double totalAvgScore;    // Tổng avgScore (để tính trung bình sau)
        int chapterCount;        // Số chapters đã gộp
    }
}
