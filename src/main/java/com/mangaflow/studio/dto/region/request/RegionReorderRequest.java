package com.mangaflow.studio.dto.region.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * ── RegionReorderRequest ──
 * DTO nhận dữ liệu từ frontend khi sắp xếp lại thứ tự các regions.
 * <p>
 * 📌 Dùng ở:
 *    - PUT /api/v1/pages/{pageId}/regions/reorder
 * <p>
 * 📌 Frontend gửi mảng region IDs theo thứ tự mới (từ dưới lên trên).
 *    Phần tử đầu tiên → sortOrder = 0 (dưới cùng)
 *    Phần tử cuối cùng → sortOrder = n-1 (trên cùng)
 * <p>
 * 📌 VD: page có 4 regions [500, 501, 502, 503] với sortOrder từ 0→3.
 *    Kéo thả region 502 lên đầu → gửi [502, 500, 501, 503].
 */
@Data
@Schema(description = "Request sắp xếp lại thứ tự regions sau kéo thả")
public class RegionReorderRequest {

    /**
     * regionIds: Mảng ID của các regions theo thứ tự mới (từ dưới lên trên).
     * Bắt buộc — không được rỗng.
     * <p>
     * 📌 Số lượng phần tử phải khớp với tổng số regions của page.
     *    Nếu không khớp → backend trả về 400 Bad Request.
     * <p>
     * 📌 @NotEmpty: kiểm tra list không null và không rỗng.
     */
    @NotEmpty(message = "Region IDs list is required")
    @Schema(description = "Danh sách region IDs theo thứ tự mới (từ dưới lên trên)",
            example = "[501, 500, 503, 502]")
    private List<Long> regionIds;
}
