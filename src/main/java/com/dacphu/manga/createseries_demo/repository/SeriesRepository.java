package com.dacphu.manga.createseries_demo.repository;

import com.dacphu.manga.createseries_demo.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeriesRepository
        extends JpaRepository<Series, Long> {

    List<Series> findByMangakaId(Long mangakaId);

}
