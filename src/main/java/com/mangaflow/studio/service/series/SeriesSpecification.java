package com.mangaflow.studio.service.series;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesAssistant;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
                // MANGAKA: chỉ thấy series mình là chủ sở hữu
                predicates.add(cb.equal(root.get("mangaka").get("id"),
                        user.getUserId()));
            } else if ("TANTOU_EDITOR".equals(role)) {
                // TANTOU_EDITOR: chỉ thấy series mình phụ trách
                predicates.add(cb.equal(root.get("tantouEditor").get("id"),
                        user.getUserId()));
            } else if ("ASSISTANT".equals(role)) {
                // ── ASSISTANT: chỉ thấy series mình đã ACCEPTED ──
                // Dùng EXISTS subquery trên bảng series_assistant:
                //
                //   WHERE EXISTS (
                //     SELECT 1 FROM series_assistant sa
                //     WHERE sa.series_id = series.id
                //       AND sa.assistant_id = currentUserId
                //       AND sa.status = 'ACCEPTED'
                //   )
                //
                // 📌 cb.exists(): trả về true nếu subquery có kết quả
                // 📌 Subquery<SeriesAssistant>: subquery từ entity SeriesAssistant
                // 📌 saRoot.get("series").get("id"): correlated subquery
                //     (liên kết với root series hiện tại)
                Subquery<SeriesAssistant> subquery = query.subquery(SeriesAssistant.class);
                Root<SeriesAssistant> saRoot = subquery.from(SeriesAssistant.class);
                subquery.select(saRoot)
                        .where(cb.and(
                                // Correlated: series_assistant.series_id = series.id
                                cb.equal(saRoot.get("series").get("id"), root.get("id")),
                                // Filter: assistant_id = currentUser.id
                                cb.equal(saRoot.get("assistant").get("id"), user.getUserId()),
                                // Filter: status = ACCEPTED
                                cb.equal(saRoot.get("status"), InvitationStatus.ACCEPTED)
                        ));
                // Thêm EXISTS vào predicates
                predicates.add(cb.exists(subquery));
            }
            // EDITORIAL_BOARD: không filter gì thêm → thấy tất cả

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
