package com.mangaflow.studio.service.feedback;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.dto.feedback.response.ReaderFeedbackResponse;
import com.mangaflow.studio.model.feedback.ReaderFeedback;
import com.mangaflow.studio.repository.feedback.ReaderFeedbackRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ── ReaderFeedbackService ──
 * Service xử lý logic đọc phản hồi độc giả cho 1 series.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Luồng xử lý:
 * ══════════════════════════════════════════════════════════════════
 *  FE gọi GET /api/v1/series/{seriesId}/reader-feedback
 *    → SeriesController.getReaderFeedback()
 *      → ReaderFeedbackService.getFeedback(seriesId)
 *         ├─ 1. Kiểm tra series tồn tại
 *         ├─ 2. Query ReaderFeedback WHERE seriesId = ?
 *         ├─ 3. Phân loại: POSITIVE → highlights, ISSUE → topIssues
 *         └─ 4. Trả về ReaderFeedbackResponse
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReaderFeedbackService {

    private final ReaderFeedbackRepository readerFeedbackRepository;
    private final SeriesRepository seriesRepository;

    /**
     * Lấy phản hồi độc giả cho 1 series.
     * <p>
     * Gồm 3 phần:
     * - highlights: Các feedback type = "POSITIVE" (lời khen)
     * - topIssues:  Các feedback type = "ISSUE" (góp ý)
     * - summary:    Tổng quan (có thể null)
     * - updatedAt:  Thời điểm feedback mới nhất
     *
     * @param seriesId ID của series
     * @return ReaderFeedbackResponse — dữ liệu cho box Reader Feedback
     * @throws AppException 404 — nếu series không tồn tại
     */
    public ReaderFeedbackResponse getFeedback(Long seriesId) {
        // ── Bước 1: Kiểm tra series tồn tại ──
        if (!seriesRepository.existsById(seriesId)) {
            throw new AppException(HttpStatus.NOT_FOUND,
                    "Series not found: " + seriesId);
        }

        // ── Bước 2: Query tất cả feedback của series ──
        List<ReaderFeedback> feedbackList = readerFeedbackRepository
                .findBySeriesIdOrderByCreatedAtDesc(seriesId);

        // ── Bước 3: Phân loại ──
        List<String> highlights = feedbackList.stream()
                .filter(f -> "POSITIVE".equalsIgnoreCase(f.getType()))
                .map(ReaderFeedback::getContent)
                .collect(Collectors.toList());

        List<String> topIssues = feedbackList.stream()
                .filter(f -> "ISSUE".equalsIgnoreCase(f.getType()))
                .map(ReaderFeedback::getContent)
                .collect(Collectors.toList());

        // ── Bước 4: Lấy updatedAt gần nhất ──
        LocalDateTime updatedAt = feedbackList.stream()
                .map(ReaderFeedback::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // ── Bước 5: Build và trả về response ──
        return ReaderFeedbackResponse.builder()
                .seriesId(seriesId)
                .highlights(highlights)
                .topIssues(topIssues)
                .updatedAt(updatedAt)
                .build();
    }
}
