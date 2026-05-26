package com.dacphu.manga.createseries_demo.controller;

import com.dacphu.manga.createseries_demo.requestDto.CreateSeriesRequest;
import com.dacphu.manga.createseries_demo.requestDto.SeriesResponse;
import com.dacphu.manga.createseries_demo.service.SeriesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/series")
public class ServiceController {
    @Autowired
    private SeriesService seriesService;

    @PostMapping
    public ResponseEntity<SeriesResponse>
    createSeries(
            @RequestBody
            @Valid CreateSeriesRequest request) {

        return ResponseEntity.ok(
                seriesService
                        .createSeries(request));
    }

    @PutMapping("/{id}/submit")
    public ResponseEntity<SeriesResponse>
    submitSeries(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                seriesService.submitSeries(id));
    }
}
