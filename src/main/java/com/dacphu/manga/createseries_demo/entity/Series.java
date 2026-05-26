package com.dacphu.manga.createseries_demo.entity;

import com.dacphu.manga.createseries_demo.model.SeriesStatus;
import com.dacphu.manga.createseries_demo.model.TargetDemographic;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;

@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Series {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String titleJp;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String synopsis;

    private String genre;

    @Enumerated(EnumType.STRING)
    private TargetDemographic targetDemographic;

    private String coverImageUrl;

    private String coverColor;

    @Enumerated(EnumType.STRING)
    private SeriesStatus status;

    private String publishFrequency;

    private String draftManuscriptUrl;

    private String pitchDocumentUrl;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "mangaka_id")
    private User mangaka;

    @ManyToOne
    @JoinColumn(name = "tantou_editor_id")
    private User tantouEditor;

    private LocalDateTime createdAt;
}
