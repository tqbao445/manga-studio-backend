package com.mangaflow.studio.model.series;

import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ── Series Entity ──
 * Ánh xạ tới bảng "series" trong database.
 *
 * 📌 Entity trung tâm của module Series — lưu toàn bộ thông tin
 *    về một bộ truyện tranh (manga) trong hệ thống.
 *
 * 📌 @Entity + @Table:
 *    JPA tự động tạo bảng "series" với các cột tương ứng field.
 *
 * 📌 Quan hệ:
 *    - mangaka (N:1 với users) → tác giả của series
 *    - tantouEditor (N:1 với users, nullable) → biên tập viên phụ trách
 *
 * 📌 @PrePersist / @PreUpdate:
 *    JPA lifecycle callback — tự động set createdAt/updatedAt
 *    mà không cần làm ở Service layer.
 */
@Entity
@Table(name = "series")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Series {

    /**
     * id: Khoá chính, tự động tăng (IDENTITY = SQL Server tự sinh).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * title: Tên series (tiếng Anh / Romaji).
     * NOT NULL — bắt buộc nhập.
     */
    @Column(nullable = false)
    private String title;

    /**
     * titleJp: Tên gốc tiếng Nhật (Kanji/Kana).
     * NULLABLE — không bắt buộc.
     * NVARCHAR → lưu được Unicode (tiếng Nhật, Hàn, Trung...).
     */
    @Column(columnDefinition = "NVARCHAR(255)")
    private String titleJp;

    /**
     * synopsis: Tóm tắt nội dung series.
     * columnDefinition = "TEXT" → kiểu TEXT trong SQL (không giới hạn 255 ký tự).
     */
    @Column(columnDefinition = "TEXT")
    private String synopsis;

    /**
     * genres: Danh sách thể loại (ACTION, FANTASY, ...).
     * @ElementCollection → tạo bảng phụ "series_genres"
     * (series_id, genre) để lưu nhiều genre cho 1 series.
     * Không cần entity riêng, Hibernate tự quản lý.
     */
    @ElementCollection
    @CollectionTable(
        name = "series_genres",
        joinColumns = @JoinColumn(name = "series_id")
    )
    @Column(name = "genre", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<Genre> genres = new ArrayList<>();

    /**
     * targetDemographics: Danh sách đối tượng độc giả (SHONEN, SHOJO, ...).
     * @ElementCollection → tạo bảng phụ "series_target_demographics"
     * (series_id, target_demographic).
     */
    @ElementCollection
    @CollectionTable(
        name = "series_target_demographics",
        joinColumns = @JoinColumn(name = "series_id")
    )
    @Column(name = "target_demographic", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<TargetDemographic> targetDemographics = new ArrayList<>();

    /**
     * status: Trạng thái hiện tại của series (DRAFT, ONGOING, ...).
     * Quản lý state machine ở SeriesService.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesStatus status;

    /**
     * coverColor: Màu nền hiển thị trên card (fallback khi không có ảnh).
     * Frontend dùng để tô màu placeholder. VD: "#e63946"
     */
    private String coverColor;

    /**
     * coverImageUrl: Đường dẫn ảnh bìa (sẽ dùng S3/Cloudinary sau này).
     * Hiện tại có thể null — frontend sẽ dùng coverColor thay thế.
     */
    private String coverImageUrl;

    /**
     * mangaka: Tác giả của series (N:1 với User).
     * LAZY fetch → chỉ load khi truy cập (tránh N+1 query).
     * JoinColumn "mangaka_id" trong bảng series.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mangaka_id", nullable = false)
    private User mangaka;

    /**
     * tantouEditor: Biên tập viên phụ trách (N:1 với users, nullable).
     * Được Editorial Board gán khi duyệt series (approve).
     * NULL nếu chưa có editor.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tantou_editor_id")
    private User tantouEditor;

    /**
     * characters: Danh sách nhân vật thuộc series này (1:N).
     * mappedBy = "series" — Character entity giữ khoá ngoại (series_id).
     * cascade = ALL + orphanRemoval = true — xoá Series → xoá luôn Characters.
     * @ToString.Exclude — tránh vòng lặp vô hạn khi log (Character.series → Series.characters → ...).
     */
    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Character> characters = new ArrayList<>();

    // ════════════════════════════════════════════════════════════════
    //  World & Plots fields — StoryProfile
    // ════════════════════════════════════════════════════════════════

    /**
     * worldLoreContent: Nội dung World Lore (HTML string từ RichEditor).
     * Lưu trực tiếp trong bảng series (cột TEXT), không cần bảng phụ.
     * FE gửi trong "storyProfile" JSON blob → PUT /{id}/story-profile.
     */
    @Column(columnDefinition = "TEXT")
    private String worldLoreContent;

    /**
     * storyRoadmap: Danh sách các Arc trong Story Roadmap.
     * @ElementCollection → tạo bảng phụ "series_story_roadmap"
     * (series_id, title, summary) — dùng RoadmapArc @Embeddable.
     */
    @ElementCollection
    @CollectionTable(
        name = "series_story_roadmap",
        joinColumns = @JoinColumn(name = "series_id")
    )
    private List<RoadmapArc> storyRoadmap = new ArrayList<>();

    /**
     * visualRefUrls: Danh sách URL ảnh Visual References từ Cloudinary.
     * @ElementCollection → tạo bảng phụ "series_visual_ref_urls"
     * (series_id, ref_url).
     * Pattern giống hệt Character.sketchUrls (@ElementCollection).
     * Upload qua multipart với key "files", merge với preservedVisualRefUrls.
     */
    @ElementCollection
    @CollectionTable(
        name = "series_visual_ref_urls",
        joinColumns = @JoinColumn(name = "series_id")
    )
    @Column(name = "ref_url", nullable = false)
    private List<String> visualRefUrls = new ArrayList<>();

    // ────────────────────────────────────────────────────────────────

    /**
     * chapterCount: Số chapter đã xuất bản.
     * Denormalized field — lưu trực tiếp để tránh join bảng chapters
     * mỗi lần hiển thị danh sách series.
     * Sẽ được cập nhật khi module Chapter được implement.
     */
    private Integer chapterCount;

    /**
     * currentRank: Thứ hạng hiện tại (từ metric gần nhất).
     * Cập nhật mỗi khi import + tính ranking.
     */
    private Integer currentRank;

    /**
     * statusNote: Ghi chú kèm trạng thái hiện tại.
     * Tại sao cần field này?
     * - Khi Tantou reject, lưu lý do reject để Mangaka biết đường sửa.
     * - Khi Chief Editor APPROVED/REJECTED, lưu quyết định kèm lý do.
     * - Tránh phải tạo bảng riêng cho log.
     */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String statusNote;

    /**
     * createdAt: Thời điểm tạo series.
     * @Column(updatable = false) → không cho phép UPDATE sau khi insert.
     * Set tự động trong @PrePersist.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * updatedAt: Thời điểm cập nhật gần nhất.
     * Set tự động trong @PrePersist và @PreUpdate.
     */
    private LocalDateTime updatedAt;

    /**
     * ── @PrePersist ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI insert entity.
     * Set createdAt + updatedAt lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ── @PreUpdate ──
     * JPA lifecycle callback — tự động chạy TRƯỚC KHI update entity.
     * Chỉ set updatedAt (createdAt giữ nguyên).
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
