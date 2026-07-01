package com.mangaflow.studio.service.ranking;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.ranking.response.AtRiskSeriesResponse;
import com.mangaflow.studio.dto.ranking.response.ImportResultResponse;
import com.mangaflow.studio.dto.ranking.response.RankingEntryResponse;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.schedule.ScheduleStatus;
import com.mangaflow.studio.model.schedule.ScheduleType;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.metric.SeriesPeriodMetricRepository;
import com.mangaflow.studio.repository.schedule.PublicationScheduleRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * ── SeriesRankingService ──
 * Service duy nhất cho cả WEEKLY và MONTHLY ranking.
 *
 * 1. Export Excel form (để EB điền điểm)
 * 2. Import Excel + tính score + gán rank
 * 3. Lấy kết quả ranking
 * 4. Auto AT_RISK: tự động phát hiện series tụt hạng 2 kỳ liên tiếp
 *    và đánh dấu AT_RISK / phục hồi về ONGOING khi cải thiện
 */
@Service
@RequiredArgsConstructor
public class SeriesRankingService {

    private final SeriesPeriodMetricRepository metricRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final PublicationScheduleRepository scheduleRepository;
    private final NotificationService notificationService;

    // ========================================================================
    // 📤 EXPORT — TẠO FILE EXCEL CHO EB ĐIỀN ĐIỂM
    // ========================================================================

    /**
     * Export weekly scoring form.
     * Chỉ lấy series WEEKLY + chapter PUBLISHED trong tuần.
     */
    public byte[] exportWeekly(String weekLabel) {
        // Parse week → start (Thứ 2) + end (Chủ Nhật)
        LocalDate[] range = parseWeekRange(weekLabel);
        LocalDateTime weekStart = range[0].atStartOfDay();
        LocalDateTime weekEnd = range[1].atTime(23, 59, 59);

        // Lấy tất cả series WEEKLY
        List<Series> weeklySeries = getSeriesByScheduleType(ScheduleType.WEEKLY);

        // Tạo Excel 1 sheet
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Weekly Chapter Votes");

            // Header
            String[] headers = {"SeriesId", "SeriesTitle", "ChapterNo", "Title", "WeekLabel", "Votes", "AvgScore"};
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Series series : weeklySeries) {
                List<Chapter> chapters = chapterRepository
                        .findBySeriesIdAndStatusAndPublishDateBetween(
                                series.getId(), ChapterStatus.PUBLISHED, weekStart, weekEnd);

                for (Chapter ch : chapters) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(series.getId());
                    row.createCell(1).setCellValue(series.getTitle());
                    row.createCell(2).setCellValue(ch.getChapterNumber());
                    row.createCell(3).setCellValue(ch.getTitle() != null ? ch.getTitle() : "");
                    row.createCell(4).setCellValue(weekLabel);
                    // Votes: để trống
                    row.createCell(5).setCellValue("");
                    // AvgScore: để trống
                    row.createCell(6).setCellValue("");
                }
            }

            return toByteArray(workbook);
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create weekly export: " + e.getMessage());
        }
    }

    /**
     * Export monthly scoring form.
     * Chỉ lấy series MONTHLY + chapter PUBLISHED trong tháng.
     */
    public byte[] exportMonthly(String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = ym.atEndOfMonth().atTime(23, 59, 59);

        List<Series> monthlySeries = getSeriesByScheduleType(ScheduleType.MONTHLY);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Chapter Scoring");

            String[] headers = {"SeriesId", "SeriesTitle", "ChapterNo", "Title", "Month", "Votes", "AvgScore"};
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Series series : monthlySeries) {
                List<Chapter> chapters = chapterRepository
                        .findBySeriesIdAndStatusAndPublishDateBetween(
                                series.getId(), ChapterStatus.PUBLISHED, monthStart, monthEnd);

                for (Chapter ch : chapters) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(series.getId());
                    row.createCell(1).setCellValue(series.getTitle());
                    row.createCell(2).setCellValue(ch.getChapterNumber());
                    row.createCell(3).setCellValue(ch.getTitle() != null ? ch.getTitle() : "");
                    row.createCell(4).setCellValue(month);
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue("");
                }
            }

            return toByteArray(workbook);
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create monthly export: " + e.getMessage());
        }
    }

    // ========================================================================
    // 📥 IMPORT — ĐỌC FILE EXCEL + TÍNH RANKING
    // ========================================================================

    /**
     * Import weekly file: đọc votes → aggregate → tính score → gán rank.
     */
    @Transactional
    public ImportResultResponse importWeekly(MultipartFile file, String weekLabel) {
        return processImport(file, weekLabel, "WEEKLY");
    }

    /**
     * Import monthly file: đọc votes → aggregate → tính score → gán rank.
     */
    @Transactional
    public ImportResultResponse importMonthly(MultipartFile file, String month) {
        validateMonthIsPast(month);
        return processImport(file, month, "MONTHLY");
    }

    /**
     * Xử lý chung cho cả weekly và monthly.
     *
     * Đọc từng dòng Excel → aggregate theo series (trong memory)
     * → Upsert SeriesPeriodMetric → sort score → gán rank.
     */
    private ImportResultResponse processImport(MultipartFile file, String periodLabel, String periodType) {
        List<ImportResultResponse.ErrorRow> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        // Map<SeriesId, Aggregate> — lưu tạm để cộng dồn
        Map<Long, Aggregate> aggregateMap = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is empty");
            }

            // Parse từng dòng (bỏ qua header row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                totalRows++;
                Row row = sheet.getRow(i);
                if (row == null) continue;

                int excelRowNum = i + 1;
                try {
                    Long seriesId = getNumericLong(row.getCell(0));
                    if (seriesId == null) {
                        throw new IllegalArgumentException("SeriesId is required at row " + excelRowNum);
                    }

                    Long votes = getNumericLong(row.getCell(5)); // Cột Votes
                    if (votes == null || votes < 0) {
                        throw new IllegalArgumentException("Votes is required and must be >= 0 at row " + excelRowNum);
                    }

                    Double avgScore = getNumericDouble(row.getCell(6)); // Cột AvgScore
                    if (avgScore == null || avgScore < 0 || avgScore > 10) {
                        throw new IllegalArgumentException("AvgScore must be between 0 and 10 at row " + excelRowNum);
                    }

                    // Validate series tồn tại
                    Series series = seriesRepository.findById(seriesId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Series not found with id: " + seriesId + " at row " + excelRowNum));

                    // Cộng dồn vào aggregate map
                    aggregateMap.merge(seriesId, new Aggregate(series, votes, avgScore, 1),
                            (a, b) -> {
                                a.totalVotes += b.totalVotes;
                                a.totalAvgScore += b.totalAvgScore;
                                a.chapterCount += b.chapterCount;
                                return a;
                            });

                    successRows++;
                } catch (Exception e) {
                    errors.add(ImportResultResponse.ErrorRow.builder()
                            .rowNumber(excelRowNum)
                            .message(e.getMessage())
                            .build());
                }
            }

            // Sau khi parse hết → aggregate và lưu SeriesPeriodMetric
            if (!aggregateMap.isEmpty()) {
                for (Aggregate agg : aggregateMap.values()) {
                    long totalVotes = agg.totalVotes;
                    double avgScore = agg.totalAvgScore / agg.chapterCount;
                    double score = totalVotes * 0.7 + avgScore * 100;

                    // Upsert
                    Optional<SeriesPeriodMetric> existing = metricRepository
                            .findBySeriesIdAndPeriodLabelAndPeriodType(
                                    agg.series.getId(), periodLabel, periodType);

                    SeriesPeriodMetric metric;
                    if (existing.isPresent()) {
                        metric = existing.get();
                        metric.setTotalVotes(totalVotes);
                        metric.setAvgScore(avgScore);
                        metric.setScore(score);
                    } else {
                        metric = SeriesPeriodMetric.builder()
                                .series(agg.series)
                                .periodLabel(periodLabel)
                                .periodType(periodType)
                                .totalVotes(totalVotes)
                                .avgScore(avgScore)
                                .score(score)
                                .build();
                    }
                    metricRepository.save(metric);
                }

                // Tính rank: sort score DESC → gán rank
                assignRanks(periodLabel, periodType);

                // Tự động phát hiện series tụt hạng 2 kỳ liên tiếp → AT_RISK
                autoFlagAtRisk(periodLabel, periodType);
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
     * Tính rank cho tất cả series trong 1 kỳ.
     * Sort score DESC → gán rank 1, 2, 3...
     * Đồng thời cập nhật currentRank trên Series.
     */
    private void assignRanks(String periodLabel, String periodType) {
        List<SeriesPeriodMetric> metrics = metricRepository
                .findByPeriodLabelAndPeriodTypeOrderByScoreDesc(periodLabel, periodType);

        for (int i = 0; i < metrics.size(); i++) {
            metrics.get(i).setRank(i + 1);
        }
        metricRepository.saveAll(metrics);

        // Cập nhật currentRank trên Series
        for (int i = 0; i < metrics.size(); i++) {
            SeriesPeriodMetric m = metrics.get(i);
            Series series = m.getSeries();
            series.setCurrentRank(m.getRank());
            seriesRepository.save(series);
        }
    }

    // ========================================================================
    // 🚩 AUTO AT_RISK — TỰ ĐỘNG PHÁT HIỆN SERIES TỤT HẠNG
    // ========================================================================

    /**
     * 📌 autoFlagAtRisk — tự động kiểm tra và cập nhật AT_RISK / ONGOING.
     *
     * Được gọi ngay sau assignRanks() trong processImport().
     * Chỉ xử lý series có status ONGOING hoặc AT_RISK.
     *
     * Rule:
     * ┌──────────┬──────────────────────────────────────────────────┐
     * │ Status   │ Điều kiện → Hành động                            │
     * ├──────────┼──────────────────────────────────────────────────┤
     * │ ONGOING  │ trend DOWN 2 kỳ liên tiếp + rank > total*70%    │
     * │          │ → AT_RISK, lưu statusNote, notify                │
     * ├──────────┼──────────────────────────────────────────────────┤
     * │ AT_RISK  │ trend UP 2 kỳ liên tiếp                         │
     * │          │ → ONGOING, xoá statusNote, notify "đã phục hồi"  │
     * └──────────┴──────────────────────────────────────────────────┘
     *
     * Nếu không đủ dữ liệu (chưa có metric kỳ trước) → bỏ qua series đó.
     */
    @Transactional
    public void autoFlagAtRisk(String periodLabel, String periodType) {
        // Lấy tất cả metric của kỳ hiện tại, đã được assignRanks() xử lý
        List<SeriesPeriodMetric> currentMetrics = metricRepository
                .findByPeriodLabelAndPeriodTypeOrderByScoreDesc(periodLabel, periodType);

        int totalSeries = currentMetrics.size();
        // rank > total * 0.7 → bottom 30%
        int bottomThreshold = (int) Math.ceil(totalSeries * 0.7);

        for (SeriesPeriodMetric metric : currentMetrics) {
            Series series = metric.getSeries();
            SeriesStatus currentStatus = series.getStatus();

            // Chỉ xử lý ONGOING hoặc AT_RISK
            if (currentStatus != SeriesStatus.ONGOING && currentStatus != SeriesStatus.AT_RISK) {
                continue;
            }

            // Lấy 2 metric gần nhất (kỳ hiện tại + kỳ trước)
            List<SeriesPeriodMetric> history = metricRepository
                    .findTop2BySeriesIdOrderByPeriodLabelDesc(series.getId());

            // Cần ít nhất 2 kỳ để tính trend
            if (history.size() < 2) continue;

            // history[0] = kỳ hiện tại (mới nhất), history[1] = kỳ trước
            int currentRank = history.get(0).getRank() != null ? history.get(0).getRank() : 0;
            int prevRank = history.get(1).getRank() != null ? history.get(1).getRank() : 0;

            // Trend giữa kỳ trước → kỳ hiện tại
            String trendThis = determineTrend(prevRank, currentRank);

            // Trend giữa kỳ trước nữa → kỳ trước (cần lấy rank của kỳ thứ 3)
            String trendPrev = determineTrend(
                    getRankBefore(history.get(1), series.getId()),
                    prevRank);

            // ── ONGOING → check có bị AT_RISK không ──
            if (currentStatus == SeriesStatus.ONGOING) {
                if ("DOWN".equals(trendThis) && "DOWN".equals(trendPrev)
                        && currentRank >= bottomThreshold) {
                    series.setStatus(SeriesStatus.AT_RISK);
                    series.setStatusNote(
                            "Auto-flagged: rank dropped for 2 consecutive periods (rank "
                                    + currentRank + "/" + totalSeries + ")");
                    seriesRepository.save(series);
                    notifyAtRisk(series, currentRank, totalSeries);
                }
            }
            // ── AT_RISK → check có hồi phục không ──
            else if (currentStatus == SeriesStatus.AT_RISK) {
                if ("UP".equals(trendThis) && "UP".equals(trendPrev)) {
                    series.setStatus(SeriesStatus.ONGOING);
                    series.setStatusNote(null);
                    seriesRepository.save(series);
                    notifyRecovered(series, currentRank, totalSeries);
                }
            }
        }
    }

    /**
     * Lấy rank của kỳ trước kỳ được chỉ định.
     * VD: history[1] là kỳ W25 → cần rank của W24.
     *
     * Nếu không tìm thấy (chưa có metric kỳ đó) → trả về 0.
     */
    private int getRankBefore(SeriesPeriodMetric metric, Long seriesId) {
        String prevLabel = computePreviousPeriodLabel(
                metric.getPeriodLabel(), metric.getPeriodType());
        if (prevLabel == null) return 0;

        return metricRepository
                .findBySeriesIdAndPeriodLabelAndPeriodType(seriesId, prevLabel, metric.getPeriodType())
                .map(m -> m.getRank() != null ? m.getRank() : 0)
                .orElse(0);
    }

    // ========================================================================
    // 📊 GET RANKING — LẤY KẾT QUẢ
    // ========================================================================

    /**
     * Lấy weekly ranking, sort theo rank.
     */
    public List<RankingEntryResponse> getWeeklyRanking(String weekLabel) {
        return getRanking(weekLabel, "WEEKLY");
    }

    /**
     * Lấy monthly ranking, sort theo rank.
     */
    public List<RankingEntryResponse> getMonthlyRanking(String month) {
        return getRanking(month, "MONTHLY");
    }

    private List<RankingEntryResponse> getRanking(String periodLabel, String periodType) {
        List<SeriesPeriodMetric> metrics = metricRepository
                .findByPeriodLabelAndPeriodTypeOrderByRankAsc(periodLabel, periodType);

        String prevLabel = computePreviousPeriodLabel(periodLabel, periodType);
        List<SeriesPeriodMetric> prevMetrics = prevLabel != null
                ? metricRepository.findByPeriodLabelAndPeriodTypeOrderByRankAsc(prevLabel, periodType)
                : List.of();
        Map<Long, Integer> prevRankMap = prevMetrics.stream()
                .collect(Collectors.toMap(
                        pm -> pm.getSeries().getId(),
                        pm -> pm.getRank() != null ? pm.getRank() : 0,
                        (a, b) -> a));

        return metrics.stream().map(m -> {
            Integer prevRank = prevRankMap.get(m.getSeries().getId());
            int currentRank = m.getRank() != null ? m.getRank() : 0;
            String trend = determineTrend(prevRank, currentRank);
            return RankingEntryResponse.builder()
                .rank(currentRank)
                .seriesId(m.getSeries().getId())
                .seriesTitle(m.getSeries().getTitle())
                .mangakaName(m.getSeries().getMangaka() != null
                        ? m.getSeries().getMangaka().getDisplayName() : null)
                .tantouEditorName(m.getSeries().getTantouEditor() != null
                        ? m.getSeries().getTantouEditor().getDisplayName() : null)
                .status(m.getSeries().getStatus() != null
                        ? m.getSeries().getStatus().name() : null)
                .totalVotes(m.getTotalVotes())
                .avgScore(m.getAvgScore())
                .score(m.getScore())
                .periodLabel(m.getPeriodLabel())
                .periodType(m.getPeriodType())
                .previousRank(prevRank)
                .trend(trend)
                .coverImageUrl(m.getSeries().getCoverImageUrl())
                .coverColor(m.getSeries().getCoverColor())
                .scheduleType(getScheduleType(m.getSeries()))
                .build();
        }).collect(Collectors.toList());
    }

    // ========================================================================
    // 🚨 GET AT-RISK — LẤY DANH SÁCH SERIES ĐANG BỊ CẢNH BÁO
    // ========================================================================

    /**
     * Lấy danh sách series đang AT_RISK kèm lịch sử ranking.
     * Dùng cho endpoint GET /api/ranking/at-risk.
     */
    public List<AtRiskSeriesResponse> getAtRiskSeries() {
        List<Series> atRiskSeries = seriesRepository.findByStatusIn(
                List.of(SeriesStatus.AT_RISK));

        return atRiskSeries.stream().map(series -> {
            // Lấy lịch sử ranking để gắn vào response
            List<SeriesPeriodMetric> history = metricRepository
                    .findBySeriesIdOrderByPeriodLabelDesc(series.getId());

            int totalSeries = (int) seriesRepository.count();

            // Build danh sách recentRanks từ history
            List<AtRiskSeriesResponse.RankHistoryEntry> recentRanks = new ArrayList<>();
            Integer prevRank = null;
            for (SeriesPeriodMetric m : history) {
                int r = m.getRank() != null ? m.getRank() : 0;
                String trend = determineTrend(prevRank, r);
                recentRanks.add(AtRiskSeriesResponse.RankHistoryEntry.builder()
                        .periodLabel(m.getPeriodLabel())
                        .rank(r)
                        .trend(trend)
                        .build());
                prevRank = r;
            }

            return AtRiskSeriesResponse.builder()
                    .seriesId(series.getId())
                    .seriesTitle(series.getTitle())
                    .coverColor(series.getCoverColor())
                    .coverImageUrl(series.getCoverImageUrl())
                    .currentRank(series.getCurrentRank() != null ? series.getCurrentRank() : 0)
                    .totalSeries(totalSeries)
                    .scheduleType(getScheduleType(series))
                    .consecutiveDownPeriods(countConsecutiveDown(history))
                    .recentRanks(recentRanks)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Lấy ScheduleType của series (WEEKLY / MONTHLY).
     * Tìm PublicationSchedule ACTIVE gần nhất → lấy scheduleType.
     * Nếu không có schedule → trả về "N/A".
     */
    private String getScheduleType(Series series) {
        List<PublicationSchedule> schedules = scheduleRepository
                .findBySeriesId(series.getId());
        return schedules.stream()
                .filter(s -> s.getStatus() == ScheduleStatus.ACTIVE)
                .findFirst()
                .map(s -> s.getScheduleType().name())
                .orElse("N/A");
    }

    /**
     * Đếm số kỳ liên tiếp có trend = DOWN (tụt hạng).
     * Dừng khi gặp UP hoặc SAME.
     *
     * Dùng để hiển thị "số kỳ liên tiếp bị tụt" trên UI.
     */
    private int countConsecutiveDown(List<SeriesPeriodMetric> history) {
        int count = 0;
        Integer prevRank = null;
        for (SeriesPeriodMetric m : history) {
            int r = m.getRank() != null ? m.getRank() : 0;
            if (prevRank != null && prevRank < r) {
                count++;
            } else if (prevRank != null && prevRank >= r) {
                break;
            }
            prevRank = r;
        }
        return count;
    }

    private String computePreviousPeriodLabel(String periodLabel, String periodType) {
        if ("WEEKLY".equals(periodType)) {
            LocalDate monday = parseWeekRange(periodLabel)[0];
            LocalDate prevMonday = monday.minusDays(7);
            int wy = prevMonday.get(WeekFields.ISO.weekBasedYear());
            int ww = prevMonday.get(WeekFields.ISO.weekOfWeekBasedYear());
            return String.format("%d-W%02d", wy, ww);
        } else if ("MONTHLY".equals(periodType)) {
            YearMonth ym = YearMonth.parse(periodLabel, DateTimeFormatter.ofPattern("yyyy-MM"));
            YearMonth prev = ym.minusMonths(1);
            return prev.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return null;
    }

    private String determineTrend(Integer prevRank, int currentRank) {
        if (prevRank == null) return "NEW";
        if (prevRank > currentRank) return "UP";
        if (prevRank < currentRank) return "DOWN";
        return "SAME";
    }

    // ========================================================================
    // 🔔 NOTIFICATION HELPERS
    // ========================================================================

    /**
     * Gửi notification cho Mangaka + Tantou khi series bị AT_RISK.
     * Type: "SERIES_AT_RISK" → FE hiển thị cảnh báo đỏ.
     */
    private void notifyAtRisk(Series series, int rank, int total) {
        String message = String.format(
                "Series '%s' is at risk of cancellation due to rank drop "
                        + "for 2 consecutive periods (rank %d/%d)",
                series.getTitle(), rank, total);

        // Gửi cho Mangaka
        notificationService.createAndSend(
                series.getMangaka().getId(),
                "SERIES_AT_RISK",
                "Warning: " + series.getTitle(),
                message,
                "SERIES",
                series.getId()
        );

        // Gửi cho Tantou Editor (nếu có)
        if (series.getTantouEditor() != null) {
            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "SERIES_AT_RISK",
                    "Warning: " + series.getTitle(),
                    message,
                    "SERIES",
                    series.getId()
            );
        }
    }

    /**
     * Gửi notification cho Mangaka + Tantou khi series hồi phục về ONGOING.
     * Type: "SERIES_RECOVERED" → FE hiển thị thông báo xanh.
     */
    private void notifyRecovered(Series series, int rank, int total) {
        String message = String.format(
                "Series '%s' has recovered, currently at rank %d/%d",
                series.getTitle(), rank, total);

        // Gửi cho Mangaka
        notificationService.createAndSend(
                series.getMangaka().getId(),
                "SERIES_RECOVERED",
                "Recovered: " + series.getTitle(),
                message,
                "SERIES",
                series.getId()
        );

        // Gửi cho Tantou Editor (nếu có)
        if (series.getTantouEditor() != null) {
            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "SERIES_RECOVERED",
                    "Recovered: " + series.getTitle(),
                    message,
                    "SERIES",
                    series.getId()
            );
        }
    }

    // ========================================================================
    // ✅ VALIDATION
    // ========================================================================

    /**
     * Validate month đã kết thúc.
     */
    private void validateMonthIsPast(String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        if (!ym.isBefore(YearMonth.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot import data for current or future month. "
                            + "Please wait until the month ends.");
        }
    }

    // ========================================================================
    // 🔧 HELPERS
    // ========================================================================

    /**
     * Parse ISO week label "2026-W25" → [Monday, Sunday].
     */
    private LocalDate[] parseWeekRange(String weekLabel) {
        if (weekLabel == null || !weekLabel.matches("\\d{4}-W\\d{2}")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invalid week format: " + weekLabel + ". Expected format: yyyy-'W'ww");
        }
        int year = Integer.parseInt(weekLabel.substring(0, 4));
        int week = Integer.parseInt(weekLabel.substring(6));

        LocalDate monday = LocalDate.of(year, 7, 1)
                .with(WeekFields.ISO.weekBasedYear(), year)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(WeekFields.ISO.dayOfWeek(), 1); // Monday
        LocalDate sunday = monday.plusDays(6);
        return new LocalDate[]{monday, sunday};
    }

    /**
     * Lấy danh sách series theo ScheduleType (WEEKLY / MONTHLY).
     */
    private List<Series> getSeriesByScheduleType(ScheduleType scheduleType) {
        List<PublicationSchedule> schedules = scheduleRepository
                .findByScheduleTypeAndStatus(scheduleType, ScheduleStatus.ACTIVE);
        return schedules.stream()
                .map(PublicationSchedule::getSeries)
                .filter(s -> s.getStatus() == SeriesStatus.ONGOING
                        || s.getStatus() == SeriesStatus.AT_RISK)
                .collect(Collectors.toList());
    }

    /**
     * Helper đọc giá trị Long từ cell (có thể là numeric hoặc string).
     */
    private Long getNumericLong(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String val = cell.getStringCellValue().trim();
            if (val.isEmpty()) return null;
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper đọc giá trị Double từ cell.
     */
    private Double getNumericDouble(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String val = cell.getStringCellValue().trim();
            if (val.isEmpty()) return null;
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Tạo style cho header Excel.
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    /**
     * Convert Workbook → byte[].
     */
    private byte[] toByteArray(Workbook workbook) {
        try (var baos = new java.io.ByteArrayOutputStream()) {
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to write Excel: " + e.getMessage());
        }
    }

    /**
     * Class tạm lưu aggregate trong memory khi import.
     */
    @lombok.AllArgsConstructor
    private static class Aggregate {
        Series series;
        long totalVotes;
        double totalAvgScore;
        int chapterCount;
    }
}
