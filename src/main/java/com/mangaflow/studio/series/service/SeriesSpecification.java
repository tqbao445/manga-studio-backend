package com.mangaflow.studio.series.service;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.series.enums.Genre;
import com.mangaflow.studio.series.enums.SeriesStatus;
import com.mangaflow.studio.series.enums.TargetDemographic;
import com.mangaflow.studio.series.model.Series;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SeriesSpecification {

    private SeriesSpecification() {}

    public static Specification<Series> buildFilter(
            SeriesStatus status, Genre genre, TargetDemographic targetDemographic,
            String search, CustomUserDetails user) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (genre != null) {
                predicates.add(cb.equal(root.get("genre"), genre));
            }

            if (targetDemographic != null) {
                predicates.add(cb.equal(root.get("targetDemographic"), targetDemographic));
            }

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + search.toLowerCase() + "%"));
            }

            String role = user.getRole();

            if ("MANGAKA".equals(role)) {
                predicates.add(cb.equal(root.get("mangaka").get("id"),
                        user.getUserId()));
            } else if ("TANTOU_EDITOR".equals(role)) {
                predicates.add(cb.equal(root.get("tantouEditor").get("id"),
                        user.getUserId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
