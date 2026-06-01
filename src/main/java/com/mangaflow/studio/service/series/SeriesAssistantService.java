package com.mangaflow.studio.service.series;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.mapper.SeriesAssistantMapper;
import com.mangaflow.studio.dto.series.request.InviteAssistantRequest;
import com.mangaflow.studio.dto.series.request.RespondInvitationRequest;
import com.mangaflow.studio.dto.series.response.SeriesAssistantResponse;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesAssistant;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.series.SeriesAssistantRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── SeriesAssistantService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến mời assistant tham gia series.
 * <p>
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * 📌 @Transactional: Tất cả method ghi dữ liệu đều chạy trong transaction.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Danh sách method (tương ứng 5 chức năng):
 * ══════════════════════════════════════════════════════════════════
 *  1. inviteAssistant()        — MANGAKA mời ASSISTANT
 *  2. getSeriesAssistants()    — Danh sách ACCEPTED của 1 series
 *  3. getPendingInvitations()  — Danh sách PENDING của 1 assistant
 *  4. respondToInvitation()    — ASSISTANT chấp nhận / từ chối
 *  5. removeAssistant()        — MANGAKA xoá assistant khỏi series
 */
@Service
@RequiredArgsConstructor
public class SeriesAssistantService {

    // ────────────────────────────────────────────────────────────
    // Repository & Dependencies
    // ────────────────────────────────────────────────────────────

    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;
    private final SeriesAssistantRepository seriesAssistantRepository;
    private final SeriesAssistantMapper seriesAssistantMapper;

    // ════════════════════════════════════════════════════════════
    // 1. INVITE ASSISTANT — MANGAKA mời ASSISTANT vào series
    // ════════════════════════════════════════════════════════════

    /**
     * MANGAKA gửi lời mời cho 1 ASSISTANT tham gia series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra series tồn tại + currentUser có phải mangaka không
     *    2. Kiểm tra assistant tồn tại + có role ASSISTANT không
     *    3. Kiểm tra đã có lời mời nào trước đó chưa:
     *       - PENDING → throw 400 "already invited"
     *       - ACCEPTED → throw 400 "already a member"
     *       - REJECTED → UPDATE lại thành PENDING (mời lại)
     *       - Không có → INSERT mới với status PENDING
     *    4. Map sang DTO và trả về
     *
     * @param seriesId     ID của series muốn mời
     * @param request      DTO chứa assistantId
     * @param currentUser  User đang đăng nhập (MANGAKA)
     * @return SeriesAssistantResponse — lời mời vừa tạo
     * @throws AppException 404 — nếu series hoặc assistant không tồn tại
     * @throws AppException 400 — nếu đã mời rồi hoặc user không phải ASSISTANT
     * @throws AppException 403 — nếu currentUser không phải mangaka của series
     */
    @Transactional
    public SeriesAssistantResponse inviteAssistant(
            Long seriesId,
            InviteAssistantRequest request,
            CustomUserDetails currentUser) {

        // ── Bước 1: Kiểm tra series tồn tại + ownership ──
        // findByIdAndMangakaId: vừa kiểm tra tồn tại, vừa kiểm tra chủ sở hữu
        Series series = seriesRepository.findByIdAndMangakaId(seriesId, currentUser.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // ── Bước 2: Kiểm tra assistant tồn tại + role ──
        User assistant = userRepository.findById(request.getAssistantId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "User not found"));

        if (assistant.getRole() != Role.ASSISTANT) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "User " + assistant.getUsername() + " is not an ASSISTANT");
        }

        // ── Bước 3: Kiểm tra lời mời trước đó ──
        // findBySeriesIdAndAssistantId trả về Optional nhờ UNIQUE constraint
        var existingOpt = seriesAssistantRepository.findBySeriesIdAndAssistantId(
                seriesId, assistant.getId());

        if (existingOpt.isPresent()) {
            SeriesAssistant existing = existingOpt.get();

            // Đã mời rồi, đang chờ phản hồi → không được mời lại
            if (existing.getStatus() == InvitationStatus.PENDING) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Assistant already invited. Please wait for their response.");
            }

            // Đã là thành viên → không cần mời lại
            if (existing.getStatus() == InvitationStatus.ACCEPTED) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Assistant is already a member of this series.");
            }

            // Trường hợp REJECTED → cập nhật lại thành PENDING (mời lại)
            existing.setStatus(InvitationStatus.PENDING);
            existing.setRespondedAt(null); // reset thời gian phản hồi cũ
            // invitedBy giữ nguyên (người mời ban đầu)
            // invitedAt giữ nguyên (thời điểm mời lần đầu)
            return seriesAssistantMapper.toResponse(
                    seriesAssistantRepository.save(existing));
        }

        // ── Bước 4: Chưa có lời mời nào → tạo mới ──
        SeriesAssistant invitation = SeriesAssistant.builder()
                .series(series)
                .assistant(assistant)
                .invitedBy(series.getMangaka()) // mangaka của series
                .status(InvitationStatus.PENDING)
                .build();

        SeriesAssistant saved = seriesAssistantRepository.save(invitation);

        // ── Bước 5: Map sang DTO và trả về ──
        return seriesAssistantMapper.toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════
    // 2. GET SERIES ASSISTANTS — Danh sách ACCEPTED của 1 series
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách assistant đã ACCEPTED của 1 series.
     * <p>
     * 📌 Chỉ trả về những assistant đã đồng ý tham gia (ACCEPTED).
     *    PENDING và REJECTED không được hiển thị trong danh sách này.
     * <p>
     * 📌 Dùng trong:
     *    - SeriesDetailPage: hiển thị team members
     *    - TaskService: lấy danh sách assistant có thể gán task
     *
     * @param seriesId ID của series
     * @return List<SeriesAssistantResponse> danh sách ACCEPTED
     * @throws AppException 404 — nếu series không tồn tại
     */
    @Transactional(readOnly = true)
    public List<SeriesAssistantResponse> getSeriesAssistants(Long seriesId) {
        // Kiểm tra series tồn tại (tránh query vào series không có thật)
        if (!seriesRepository.existsById(seriesId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Series not found");
        }

        // Query tất cả record ACCEPTED của series này
        List<SeriesAssistant> assistants = seriesAssistantRepository
                .findBySeriesIdAndStatus(seriesId, InvitationStatus.ACCEPTED);

        // Map từng entity sang DTO
        return assistants.stream()
                .map(seriesAssistantMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════
    // 3. GET PENDING INVITATIONS — Lời mời đang chờ của ASSISTANT
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách lời mời PENDING của assistant hiện tại.
     * <p>
     * 📌 Dùng trong:
     *    - InvitationsPage (frontend): hiển thị danh sách lời mời đang chờ
     *    - Notification: đếm số lời mời chưa đọc
     * <p>
     * 📌 Chỉ trả về PENDING — ACCEPTED và REJECTED không hiển thị ở đây.
     *
     * @param currentUser User đang đăng nhập (ASSISTANT)
     * @return List<SeriesAssistantResponse> danh sách lời mời PENDING
     */
    @Transactional(readOnly = true)
    public List<SeriesAssistantResponse> getPendingInvitations(CustomUserDetails currentUser) {
        // Query tất cả lời mời PENDING của assistant này
        List<SeriesAssistant> invitations = seriesAssistantRepository
                .findByAssistantIdAndStatus(currentUser.getUserId(), InvitationStatus.PENDING);

        // Map sang DTO
        return invitations.stream()
                .map(seriesAssistantMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════
    // 4. RESPOND TO INVITATION — ASSISTANT chấp nhận / từ chối
    // ════════════════════════════════════════════════════════════

    /**
     * ASSISTANT phản hồi lời mời tham gia series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra invitation tồn tại
     *    2. Kiểm tra invitation thuộc về currentUser
     *    3. Kiểm tra invitation đang ở trạng thái PENDING
     *    4. Kiểm tra request.status là ACCEPTED hoặc REJECTED (không phải PENDING)
     *    5. Cập nhật status + respondedAt
     *    6. Map sang DTO và trả về
     *
     * @param invitationId ID của lời mời
     * @param request      DTO chứa status (ACCEPTED / REJECTED)
     * @param currentUser  User đang đăng nhập (ASSISTANT)
     * @return SeriesAssistantResponse — lời mời sau khi phản hồi
     * @throws AppException 404 — nếu không tìm thấy lời mời
     * @throws AppException 403 — nếu lời mời không thuộc về currentUser
     * @throws AppException 400 — nếu lời mời không ở PENDING hoặc status không hợp lệ
     */
    @Transactional
    public SeriesAssistantResponse respondToInvitation(
            Long invitationId,
            RespondInvitationRequest request,
            CustomUserDetails currentUser) {

        // ── Bước 1: Kiểm tra invitation tồn tại ──
        SeriesAssistant invitation = seriesAssistantRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Invitation not found"));

        // ── Bước 2: Kiểm tra invitation thuộc về currentUser ──
        // Chỉ assistant được mời mới có quyền phản hồi
        if (!invitation.getAssistant().getId().equals(currentUser.getUserId())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "This invitation is not for you");
        }

        // ── Bước 3: Kiểm tra invitation đang PENDING ──
        // Không thể phản hồi lại lời mời đã ACCEPTED hoặc REJECTED
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Invitation is not pending. Current status: " + invitation.getStatus());
        }

        // ── Bước 4: Kiểm tra status hợp lệ ──
        // Chỉ chấp nhận ACCEPTED hoặc REJECTED, không cho phép PENDING
        InvitationStatus newStatus = request.getStatus();
        if (newStatus != InvitationStatus.ACCEPTED
                && newStatus != InvitationStatus.REJECTED) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Status must be ACCEPTED or REJECTED");
        }

        // ── Bước 5: Cập nhật status + respondedAt ──
        invitation.setStatus(newStatus);
        invitation.setRespondedAt(LocalDateTime.now());

        SeriesAssistant saved = seriesAssistantRepository.save(invitation);

        // ── Bước 6: Map sang DTO và trả về ──
        return seriesAssistantMapper.toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════
    // 5. REMOVE ASSISTANT — MANGAKA xoá assistant khỏi series
    // ════════════════════════════════════════════════════════════

    /**
     * MANGAKA xoá assistant khỏi series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra series tồn tại + currentUser có phải mangaka không
     *    2. Xoá record series_assistant (bất kể trạng thái nào)
     *       - ACCEPTED: xoá khỏi team
     *       - PENDING: huỷ lời mời
     *       - REJECTED: xoá lịch sử (tuỳ chọn)
     * <p>
     * 📌 Lưu ý:
     *    - Nếu assistant đang có task trong series → vẫn xoá được
     *      (task sẽ không có người làm → cần reassign)
     *    - Task không bị xoá theo — chỉ mất liên kết với assistant
     *
     * @param seriesId    ID của series
     * @param assistantId ID của assistant cần xoá
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @throws AppException 404 — nếu series không tồn tại hoặc không phải chủ
     * @throws AppException 404 — nếu không tìm thấy record series_assistant
     */
    @Transactional
    public void removeAssistant(Long seriesId, Long assistantId, CustomUserDetails currentUser) {
        // ── Bước 1: Kiểm tra series tồn tại + ownership ──
        seriesRepository.findByIdAndMangakaId(seriesId, currentUser.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // ── Bước 2: Kiểm tra record tồn tại ──
        // findBySeriesIdAndAssistantId: kiểm tra cặp (seriesId, assistantId) có tồn tại không
        var existingOpt = seriesAssistantRepository.findBySeriesIdAndAssistantId(
                seriesId, assistantId);

        if (existingOpt.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND,
                    "Assistant is not associated with this series");
        }

        // ── Bước 3: Xoá record ──
        // Dùng deleteBySeriesIdAndAssistantId thay vì delete(entity)
        // để tránh phải load entity trước (tối ưu performance)
        seriesAssistantRepository.deleteBySeriesIdAndAssistantId(seriesId, assistantId);
    }
}
