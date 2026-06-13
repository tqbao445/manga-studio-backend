package com.mangaflow.studio.dto.schedule.mapper;

import com.mangaflow.studio.dto.schedule.request.CreateScheduleRequest;
import com.mangaflow.studio.dto.schedule.response.ScheduleResponse;
import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.series.Series;
import org.mapstruct.*;

/**
 * ── ScheduleMapper ──
 * MapStruct mapper chuyển đổi giữa PublicationSchedule entity và DTO.
 *
 * ═══════════════════════════════════════════════════
 *  3 method chính:
 *    1. toResponse():      Entity → Response DTO (đọc dữ liệu)
 *    2. toEntity():        Request → Entity (tạo mới)
 *    3. updateEntity():    Request → merge vào Entity (cập nhật, null-safe)
 * ═══════════════════════════════════════════════════
 */
@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    /**
     * toResponse: Chuyển PublicationSchedule entity → ScheduleResponse DTO.
     * Map series.id → seriesId, series.title → seriesTitle.
     *
     * @param schedule entity từ database
     * @return ScheduleResponse để trả về frontend
     */
    @Mapping(target = "seriesId", source = "series.id")
    @Mapping(target = "seriesTitle", source = "series.title")
    ScheduleResponse toResponse(PublicationSchedule schedule);

    /**
     * toEntity: Chuyển CreateScheduleRequest + Series → PublicationSchedule entity.
     * Dùng cho API tạo mới schedule.
     *
     * Các field mặc định:
     *   - nextChapterNumber = 1
     *   - missCount = 0
     *   - status = ACTIVE
     *   - createdAt / updatedAt = null → @PrePersist tự xử lý
     *
     * @param request DTO từ client
     * @param series  Series entity từ database
     * @return PublicationSchedule entity sẵn sàng để save
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "series", source = "series")
    @Mapping(target = "nextChapterNumber", constant = "1")
    @Mapping(target = "missCount", constant = "0")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PublicationSchedule toEntity(CreateScheduleRequest request, Series series);

    /**
     * updateEntity: Cập nhật null-safe các field từ request vào entity.
     * Dùng cho API PUT (sửa schedule, đổi WEEKLY ↔ MONTHLY, ...).
     *
     * ⚠️ Chỉ update các field cho phép:
     *    - scheduleType, dayOfWeek, dayOfMonth, startDate
     * Các field KHÔNG update:
     *    - id, series, nextChapterNumber, missCount, status, createdAt, updatedAt
     *
     * 📌 Null-safe:
     *    Field null trong request → giữ nguyên giá trị cũ.
     *    Chỉ field != null mới được ghi đè.
     *
     * @param schedule entity cần cập nhật
     * @param request  DTO chứa dữ liệu mới
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "nextChapterNumber", ignore = true)
    @Mapping(target = "missCount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget PublicationSchedule schedule, CreateScheduleRequest request);
}
