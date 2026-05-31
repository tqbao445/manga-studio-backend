package com.mangaflow.studio.model.chapter;

import com.mangaflow.studio.model.series.Series;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chapter", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"series_id", "chapter_number"}) //không trùng số chapter trong cùng series
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //- @ManyToOne(fetch = LAZY) → Series (không dùng raw seriesId như Page)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    private String title;

    @Builder.Default
    @Column(name = "page_count")
    private Integer pageCount = 0;

    @Builder.Default
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    private LocalDate deadline;

    @Column(name = "publish_date")
    private LocalDateTime publishDate;

    @Enumerated(EnumType.STRING) //cho status, default DRAFT
    @Column(nullable = false)
    @Builder.Default
    private ChapterStatus status = ChapterStatus.DRAFT;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    //Prepersist, PreUpdate cho timestamps (giống Series.java, Page.java)
}
