package com.mangaflow.studio.model.series;

import org.springframework.data.domain.Sort;

public enum SeriesSortBy {
    UPDATED_AT_DESC("updatedAt", Sort.Direction.DESC),
    UPDATED_AT_ASC("updatedAt", Sort.Direction.ASC),
    CREATED_AT_DESC("createdAt", Sort.Direction.DESC),
    CREATED_AT_ASC("createdAt", Sort.Direction.ASC),
    TITLE_ASC("title", Sort.Direction.ASC),
    TITLE_DESC("title", Sort.Direction.DESC);

    private final String property;
    private final Sort.Direction direction;

    SeriesSortBy(String property, Sort.Direction direction) {
        this.property = property;
        this.direction = direction;
    }

    public Sort getSort() {
        return Sort.by(direction, property);
    }
}
