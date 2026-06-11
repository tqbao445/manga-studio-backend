package com.mangaflow.studio.controller.meeting;

import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.meeting.*;
import com.mangaflow.studio.service.meeting.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Meeting", description = "API quản lý cuộc họp phê duyệt series")
public class MeetingController {

    private final MeetingService meetingService;

    @Operation(summary = "Tạo cuộc họp mới",
               description = "Chief Editor tạo cuộc họp cho series đang ở trạng thái PENDING_BOARD_VOTE. " +
                           "Yêu cầu gửi kèm danh sách participant IDs (EDITORIAL_BOARD + TANTOU_EDITOR).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tạo meeting thành công"),
        @ApiResponse(responseCode = "400", description = "Series không ở trạng thái PENDING_BOARD_VOTE hoặc participant không hợp lệ"),
        @ApiResponse(responseCode = "404", description = "Series không tồn tại")
    })
    @PostMapping("/meetings")
    @PreAuthorize("hasRole('CHIEF_EDITOR')")
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(request, user));
    }

    @Operation(summary = "Xem chi tiết cuộc họp",
               description = "Lấy thông tin cuộc họp bao gồm participant + kết quả vote (nếu có). " +
                           "Bất kỳ user nào đã đăng nhập đều xem được.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Meeting không tồn tại")
    })
    @GetMapping("/meetings/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(@PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeeting(id));
    }

    @Operation(summary = "Danh sách cuộc họp của series",
               description = "Lấy tất cả cuộc họp của 1 series, sắp xếp mới nhất trước.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    @GetMapping("/series/{seriesId}/meetings")
    public ResponseEntity<List<MeetingResponse>> getMeetingsBySeries(
            @PathVariable Long seriesId) {
        return ResponseEntity.ok(meetingService.getMeetingsBySeries(seriesId));
    }

    @Operation(summary = "Danh sách cuộc họp của user hiện tại",
               description = "Lấy tất cả cuộc họp mà user đang đăng nhập được mời tham gia. " +
                           "Sắp xếp mới nhất trước. Dùng cho EditorialBoardPage.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    @GetMapping("/meetings/user")
    public ResponseEntity<List<MeetingResponse>> getMeetingsForUser(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(meetingService.getMeetingsForUser(user.getUserId()));
    }

    @Operation(summary = "Danh sách tiêu chí chấm điểm active",
               description = "Lấy danh sách tiêu chí chấm điểm đang được kích hoạt (is_active = true), " +
                           "sắp xếp theo sortOrder. Dùng để render form vote.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    @GetMapping("/criteria")
    public ResponseEntity<List<CriterionResponse>> getActiveCriteria() {
        return ResponseEntity.ok(meetingService.getActiveCriteria());
    }

    @Operation(summary = "Bỏ phiếu cho cuộc họp",
               description = "EDITORIAL_BOARD member bỏ phiếu YES/NO và chấm điểm 1-10 cho từng tiêu chí. " +
                           "Cho phép gọi lại để đổi ý (upsert).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vote thành công"),
        @ApiResponse(responseCode = "400", description = "Meeting đã đóng hoặc scores không hợp lệ"),
        @ApiResponse(responseCode = "403", description = "Bạn không phải participant của meeting này")
    })
    @PostMapping("/meetings/{id}/vote")
    @PreAuthorize("hasRole('EDITORIAL_BOARD')")
    public ResponseEntity<VoteResponse> castVote(
            @PathVariable Long id,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(meetingService.castVote(id, request, user));
    }

    @Operation(summary = "Xem kết quả vote",
               description = "Xem kết quả vote hiện tại của cuộc họp: số phiếu YES/NO, điểm chi tiết, " +
                           "và phiếu của user hiện tại.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Meeting không tồn tại")
    })
    @GetMapping("/meetings/{id}/votes")
    public ResponseEntity<VoteResponse> getVoteResults(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(meetingService.getVoteResults(id, user.getUserId()));
    }

    @Operation(summary = "Quyết định cuối cùng",
               description = "Chief Editor ra quyết định APPROVED (duyệt) hoặc REJECTED (từ chối). " +
                           "APPROVED => series chuyển ONGOING, REJECTED => series quay DRAFT. " +
                           "Chỉ được gọi 1 lần / meeting.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quyết định thành công"),
        @ApiResponse(responseCode = "400", description = "Meeting đã có decision rồi hoặc giá trị không hợp lệ"),
        @ApiResponse(responseCode = "404", description = "Meeting không tồn tại")
    })
    @PostMapping("/meetings/{id}/decision")
    @PreAuthorize("hasRole('CHIEF_EDITOR')")
    public ResponseEntity<MeetingResponse> makeDecision(
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(meetingService.makeDecision(id, request, user));
    }
}
