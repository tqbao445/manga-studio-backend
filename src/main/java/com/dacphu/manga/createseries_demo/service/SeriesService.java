package com.dacphu.manga.createseries_demo.service;

import com.dacphu.manga.createseries_demo.entity.Series;
import com.dacphu.manga.createseries_demo.entity.User;
import com.dacphu.manga.createseries_demo.model.Role;
import com.dacphu.manga.createseries_demo.model.SeriesStatus;
import com.dacphu.manga.createseries_demo.repository.SeriesRepository;
import com.dacphu.manga.createseries_demo.repository.UserRepository;
import com.dacphu.manga.createseries_demo.requestDto.CreateSeriesRequest;
import com.dacphu.manga.createseries_demo.requestDto.SeriesResponse;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.dacphu.manga.createseries_demo.model.TargetDemographic;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SeriesService{
    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private UserRepository userRepository;

    private SeriesResponse mapToResponse(
            Series series) {

        return SeriesResponse.builder()
                .id(series.getId())
                .title(series.getTitle())
                .synopsis(series.getSynopsis())
                .genre(series.getGenre())
                .status(series.getStatus().name())
                .mangakaName(
                        series.getMangaka()
                                .getDisplayName()
                )
                .tantouEditorName(
                        series.getTantouEditor() != null
                                ? series.getTantouEditor()
                                  .getDisplayName()
                                : null
                )
                .createdAt(
                        series.getCreatedAt()
                )
                .build();
    }


    public SeriesResponse createSeries(
            CreateSeriesRequest request) {

        User currentUser =
                userRepository.findById(1L)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        if (currentUser.getRole() != Role.MANGAKA) {
            throw new RuntimeException(
                    "Only mangaka can create series");
        }

        User editor = null;

        if (request.getTantouEditorId() != null) {

            editor = userRepository.findById(
                            request.getTantouEditorId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Editor not found"));
        }

        Series series = Series.builder()
                .title(request.getTitle())
                .titleJp(request.getTitleJp())
                .synopsis(request.getSynopsis())
                .genre(request.getGenre())
                .targetDemographic(
                        TargetDemographic.valueOf(
                                request.getTargetDemographic()))
                .coverColor(request.getCoverColor())
                .publishFrequency(
                        request.getPublishFrequency())
                .status(SeriesStatus.DRAFT)
                .createdBy(currentUser)
                .mangaka(currentUser)
                .tantouEditor(editor)
                .createdAt(LocalDateTime.now())
                .build();

        seriesRepository.save(series);

        return mapToResponse(series);
    }

    public SeriesResponse submitSeries(Long id) {

        Series series =
                seriesRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Series not found"));

        if (series.getStatus()
                != SeriesStatus.DRAFT) {

            throw new RuntimeException(
                    "Only draft can submit");
        }

        series.setStatus(
                SeriesStatus.IN_REVIEW);

        seriesRepository.save(series);

        return mapToResponse(series);
    }
}
