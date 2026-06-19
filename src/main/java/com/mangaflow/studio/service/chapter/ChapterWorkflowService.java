package com.mangaflow.studio.service.chapter;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.chapter.mapper.ChapterMapper;
import com.mangaflow.studio.dto.chapter.response.ChapterResponse;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.service.common.WebSocketService;
import com.mangaflow.studio.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterWorkflowService {

    private final ChapterRepository chapterRepository;
    private final ChapterMapper chapterMapper;
    private final WebSocketService webSocketService;
    private final NotificationService notificationService;

    @Transactional
    public ChapterResponse submitForReview(Long id, CustomUserDetails user) {
        Chapter chapter = chapterRepository.findByIdWithSeries(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        Series series = chapter.getSeries();

        if (!series.getMangaka().getId().equals(user.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the series owner can submit chapters");
        }

        if (chapter.getStatus() != ChapterStatus.DRAFT && chapter.getStatus() != ChapterStatus.REVISION_REQUIRED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT or REVISION_REQUIRED chapters can be submitted");
        }

        if (chapter.getProgressPercent() == null || chapter.getProgressPercent() < 100) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "All pages must be completed before submitting for review");
        }

        if (series.getStatus() != SeriesStatus.ONGOING) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series must be ONGOING before chapters can be submitted for review");
        }

        chapter.setStatus(ChapterStatus.IN_REVIEW);
        ChapterResponse response = chapterMapper.toResponse(chapterRepository.save(chapter));

        if (series.getTantouEditor() != null) {
            webSocketService.sendToUser(series.getTantouEditor().getId(),
                    "CHAPTER_SUBMITTED",
                    "Ch." + chapter.getChapterNumber() + " of \"" + series.getTitle() + "\" submitted for review");

            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "CHAPTER_SUBMITTED",
                    "New chapter to review: " + series.getTitle(),
                    "Ch." + chapter.getChapterNumber() + " \"" + (chapter.getTitle() != null ? chapter.getTitle() : "")
                            + "\" has been submitted for your review.",
                    "CHAPTER",
                    chapter.getId()
            );
        }

        return response;
    }

    @Transactional
    public ChapterResponse tantouApprove(Long id, CustomUserDetails user) {
        Chapter chapter = chapterRepository.findByIdWithSeries(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        Series series = chapter.getSeries();

        if (series.getTantouEditor() == null || !series.getTantouEditor().getId().equals(user.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the assigned tantou editor");
        }

        if (chapter.getStatus() != ChapterStatus.IN_REVIEW) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chapter is not in review");
        }

        chapter.setStatus(ChapterStatus.APPROVED);
        ChapterResponse response = chapterMapper.toResponse(chapterRepository.save(chapter));

        webSocketService.sendToUser(series.getMangaka().getId(),
                "CHAPTER_APPROVED",
                "Ch." + chapter.getChapterNumber() + " of \"" + series.getTitle() + "\" has been approved");

        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_APPROVED",
                "Chapter approved",
                "Ch." + chapter.getChapterNumber() + " has been approved by your tantou editor.",
                "CHAPTER",
                chapter.getId()
        );

        return response;
    }

    @Transactional
    public ChapterResponse tantouRequestRevision(Long id, CustomUserDetails user) {
        Chapter chapter = chapterRepository.findByIdWithSeries(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        Series series = chapter.getSeries();

        if (series.getTantouEditor() == null || !series.getTantouEditor().getId().equals(user.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the assigned tantou editor");
        }

        if (chapter.getStatus() != ChapterStatus.IN_REVIEW) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chapter is not in review");
        }

        chapter.setStatus(ChapterStatus.REVISION_REQUIRED);
        ChapterResponse response = chapterMapper.toResponse(chapterRepository.save(chapter));

        webSocketService.sendToUser(series.getMangaka().getId(),
                "CHAPTER_REVISION_REQUESTED",
                "Ch." + chapter.getChapterNumber() + " of \"" + series.getTitle() + "\" needs revision");

        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_REVISION_REQUESTED",
                "Revision needed",
                "Ch." + chapter.getChapterNumber() + " requires revisions before it can be approved.",
                "CHAPTER",
                chapter.getId()
        );

        return response;
    }

    @Transactional
    public ChapterResponse publish(Long id, CustomUserDetails user) {
        Chapter chapter = chapterRepository.findByIdWithSeries(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chapter not found"));

        Series series = chapter.getSeries();

        if (!"EDITORIAL_BOARD".equals(user.getRole()) && !"CHIEF_EDITOR".equals(user.getRole())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only Editorial Board members can publish chapters");
        }

        if (chapter.getStatus() != ChapterStatus.APPROVED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chapter must be approved before publishing");
        }

        if (series.getStatus() != SeriesStatus.ONGOING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Series must be ONGOING to publish chapters");
        }

        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setPublishDate(java.time.LocalDateTime.now());
        ChapterResponse response = chapterMapper.toResponse(chapterRepository.save(chapter));

        String message = "Ch." + chapter.getChapterNumber() + " of \"" + series.getTitle() + "\" has been published";

        webSocketService.sendToUser(series.getMangaka().getId(), "CHAPTER_PUBLISHED", message);

        notificationService.createAndSend(
                series.getMangaka().getId(),
                "CHAPTER_PUBLISHED",
                "Chapter published",
                "Ch." + chapter.getChapterNumber() + " has been published.",
                "CHAPTER",
                chapter.getId()
        );

        if (series.getTantouEditor() != null) {
            webSocketService.sendToUser(series.getTantouEditor().getId(), "CHAPTER_PUBLISHED", message);

            notificationService.createAndSend(
                    series.getTantouEditor().getId(),
                    "CHAPTER_PUBLISHED",
                    "Chapter published",
                    "Ch." + chapter.getChapterNumber() + " has been published by Editorial Board.",
                    "CHAPTER",
                    chapter.getId()
            );
        }

        return response;
    }
}
