package com.mangaflow.studio.service.series;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.series.mapper.SeriesTantouMapper;
import com.mangaflow.studio.dto.series.request.InviteTantouRequest;
import com.mangaflow.studio.dto.series.request.RespondInvitationRequest;
import com.mangaflow.studio.dto.series.response.SeriesTantouResponse;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.series.InvitationStatus;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesTantouInvitation;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.repository.series.SeriesTantouInvitationRepository;
import com.mangaflow.studio.service.common.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ── SeriesTantouInvitationService ──
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến mời tantou
 * tham gia kiểm duyệt series (review trước khi đưa lên EB vote).
 * <p>
 * 📌 @Service: Spring Bean — chứa business logic, quản lý transaction.
 * 📌 @RequiredArgsConstructor: Lombok — DI constructor cho tất cả field final.
 * <p>
 * ══════════════════════════════════════════════════════════════════
 *  Danh sách method (tương ứng 5 chức năng):
 * ══════════════════════════════════════════════════════════════════
 *  1. inviteTantou()             — MANGAKA mời TANTOU_EDITOR
 *  2. getSeriesTantouInvitations() — Danh sách lời mời của 1 series
 *  3. getPendingInvitations()    — Danh sách PENDING của 1 tantou
 *  4. respondToInvitation()      — TANTOU chấp nhận / từ chối
 *  5. removeTantouInvitation()   — MANGAKA xoá/huỷ lời mời
 */
@Service
@RequiredArgsConstructor
public class SeriesTantouInvitationService {

    // ────────────────────────────────────────────────────────────
    // Repository & Dependencies
    // ────────────────────────────────────────────────────────────

    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;
    private final SeriesTantouInvitationRepository seriesTantouInvitationRepository;
    private final SeriesTantouMapper seriesTantouMapper;
    private final WebSocketService webSocketService;

    // ════════════════════════════════════════════════════════════
    // 1. INVITE TANTOU — MANGAKA mời TANTOU_EDITOR duyệt series
    // ════════════════════════════════════════════════════════════

    /**
     * MANGAKA gửi lời mời cho 1 TANTOU_EDITOR tham gia kiểm duyệt series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra series tồn tại + currentUser có phải mangaka không
     *    2. Kiểm tra tantou tồn tại + có role TANTOU_EDITOR không
     *    3. Kiểm tra đã có lời mời nào trước đó chưa:
     *       - PENDING → throw 400 "already invited"
     *       - ACCEPTED → throw 400 "already a reviewer"
     *       - REJECTED → UPDATE lại thành PENDING (mời lại)
     *       - Không có → INSERT mới với status PENDING
     *    4. Map sang DTO và trả về
     *
     * @param seriesId    ID của series muốn mời
     * @param request     DTO chứa tantouId
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @return SeriesTantouResponse — lời mời vừa tạo
     * @throws AppException 404 — nếu series hoặc tantou không tồn tại
     * @throws AppException 400 — nếu đã mời rồi hoặc user không phải TANTOU_EDITOR
     * @throws AppException 403 — nếu currentUser không phải mangaka của series
     */
    @Transactional
    public SeriesTantouResponse inviteTantou(
            Long seriesId,
            InviteTantouRequest request,
            CustomUserDetails currentUser) {

        // ── Bước 1: Kiểm tra series tồn tại + ownership ──
        // findByIdAndMangakaId: vừa kiểm tra tồn tại, vừa kiểm tra chủ sở hữu
        Series series = seriesRepository.findByIdAndMangakaId(seriesId, currentUser.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // ── Kiểm tra series đã có tantouEditor chưa ──
        // Trong thực tế, 1 series chỉ có 1 tantou phụ trách (giống 1 editor / 1 cuốn sách).
        // Nếu đã có → báo lỗi, yêu cầu xoá tantou cũ trước mới mời được người mới.
        if (series.getTantouEditor() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series already has a tantou reviewer: "
                    + series.getTantouEditor().getDisplayName()
                    + ". Please remove them first.");
        }

        // ── Bước 2: Kiểm tra tantou tồn tại + role ──
        User tantou = userRepository.findById(request.getTantouId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "User not found"));

        if (tantou.getRole() != Role.TANTOU_EDITOR) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "User " + tantou.getUsername() + " is not a TANTOU_EDITOR");
        }

        // ── Bước 3: Kiểm tra lời mời trước đó ──
        // findBySeriesIdAndTantouId trả về Optional nhờ UNIQUE constraint
        var existingOpt = seriesTantouInvitationRepository.findBySeriesIdAndTantouId(
                seriesId, tantou.getId());

        if (existingOpt.isPresent()) {
            SeriesTantouInvitation existing = existingOpt.get();

            // Đã mời rồi, đang chờ phản hồi → không được mời lại
            if (existing.getStatus() == InvitationStatus.PENDING) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Tantou already invited. Please wait for their response.");
            }

            // Đã là reviewer → không cần mời lại
            if (existing.getStatus() == InvitationStatus.ACCEPTED) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Tantou is already a reviewer of this series.");
            }

            // Trường hợp REJECTED → cập nhật lại thành PENDING (mời lại)
            existing.setStatus(InvitationStatus.PENDING);
            existing.setRespondedAt(null); // reset thời gian phản hồi cũ
            // invitedBy giữ nguyên (người mời ban đầu)
            // invitedAt giữ nguyên (thời điểm mời lần đầu)
            SeriesTantouResponse existingResponse = seriesTantouMapper.toResponse(
                    seriesTantouInvitationRepository.save(existing));

            // Push real-time cho TANTOU biết có lời mời mới (hoặc mời lại)
            webSocketService.sendToUser(tantou.getId(), "TANTOU_INVITATION_SENT", existingResponse);
            return existingResponse;
        }

        // ── Bước 4: Chưa có lời mời nào → tạo mới ──
        SeriesTantouInvitation invitation = SeriesTantouInvitation.builder()
                .series(series)
                .tantou(tantou)
                .invitedBy(series.getMangaka()) // mangaka của series
                .status(InvitationStatus.PENDING)
                .build();

        SeriesTantouInvitation saved = seriesTantouInvitationRepository.save(invitation);

        // ── Bước 5: Map sang DTO và trả về ──
        SeriesTantouResponse response = seriesTantouMapper.toResponse(saved);

        // Push real-time cho TANTOU biết có lời mời mới
        webSocketService.sendToUser(tantou.getId(), "TANTOU_INVITATION_SENT", response);

        return response;
    }

    // ════════════════════════════════════════════════════════════
    // 2. GET SERIES TANTOU INVITATIONS — Danh sách lời mời của 1 series
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách tất cả lời mời tantou của 1 series.
     * <p>
     * Bao gồm tất cả trạng thái: PENDING, ACCEPTED, REJECTED.
     * <p>
     * 📌 Dùng trong:
     *    - SeriesDetailPage: hiển thị trạng thái mời tantou
     *
     * @param seriesId ID của series
     * @return List<SeriesTantouResponse> danh sách lời mời
     * @throws AppException 404 — nếu series không tồn tại
     */
    @Transactional(readOnly = true)
    public List<SeriesTantouResponse> getSeriesTantouInvitations(Long seriesId) {
        // Kiểm tra series tồn tại (tránh query vào series không có thật)
        if (!seriesRepository.existsById(seriesId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Series not found");
        }

        // Query tất cả record của series này (bất kể trạng thái nào)
        List<SeriesTantouInvitation> invitations = seriesTantouInvitationRepository
                .findBySeriesIdAndStatus(seriesId, InvitationStatus.PENDING);
        // Thêm cả ACCEPTED và REJECTED để hiển thị đầy đủ
        invitations.addAll(seriesTantouInvitationRepository
                .findBySeriesIdAndStatus(seriesId, InvitationStatus.ACCEPTED));
        invitations.addAll(seriesTantouInvitationRepository
                .findBySeriesIdAndStatus(seriesId, InvitationStatus.REJECTED));

        // Map từng entity sang DTO
        return invitations.stream()
                .map(seriesTantouMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════
    // 3. GET PENDING INVITATIONS — Lời mời đang chờ của TANTOU
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách lời mời PENDING của tantou hiện tại.
     * <p>
     * 📌 Dùng trong:
     *    - InvitationsPage (frontend): hiển thị danh sách lời mời đang chờ
     *    - Notification: đếm số lời mời chưa đọc
     * <p>
     * 📌 Chỉ trả về PENDING — ACCEPTED và REJECTED không hiển thị ở đây.
     *
     * @param currentUser User đang đăng nhập (TANTOU_EDITOR)
     * @return List<SeriesTantouResponse> danh sách lời mời PENDING
     */
    @Transactional(readOnly = true)
    public List<SeriesTantouResponse> getPendingInvitations(CustomUserDetails currentUser) {
        // Query tất cả lời mời PENDING của tantou này
        List<SeriesTantouInvitation> invitations = seriesTantouInvitationRepository
                .findByTantouIdAndStatus(currentUser.getUserId(), InvitationStatus.PENDING);

        // Map sang DTO
        return invitations.stream()
                .map(seriesTantouMapper::toResponse)
                .toList();
    }

    // ════════════════════════════════════════════════════════════
    // 4. RESPOND TO INVITATION — TANTOU chấp nhận / từ chối
    // ════════════════════════════════════════════════════════════

    /**
     * TANTOU_EDITOR phản hồi lời mời tham gia kiểm duyệt series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra invitation tồn tại
     *    2. Kiểm tra invitation thuộc về currentUser
     *    3. Kiểm tra invitation đang ở trạng thái PENDING
     *    4. Kiểm tra request.status là ACCEPTED hoặc REJECTED (không phải PENDING)
     *    5. Nếu ACCEPTED → gán series.tantouEditor = tantou này
     *    6. Cập nhật status + respondedAt
     *    7. Map sang DTO và trả về
     *
     * @param invitationId ID của lời mời
     * @param request      DTO chứa status (ACCEPTED / REJECTED)
     * @param currentUser  User đang đăng nhập (TANTOU_EDITOR)
     * @return SeriesTantouResponse — lời mời sau khi phản hồi
     * @throws AppException 404 — nếu không tìm thấy lời mời
     * @throws AppException 403 — nếu lời mời không thuộc về currentUser
     * @throws AppException 400 — nếu lời mời không ở PENDING hoặc status không hợp lệ
     */
    @Transactional
    public SeriesTantouResponse respondToInvitation(
            Long invitationId,
            RespondInvitationRequest request,
            CustomUserDetails currentUser) {

        // ── Bước 1: Kiểm tra invitation tồn tại ──
        SeriesTantouInvitation invitation = seriesTantouInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Invitation not found"));

        // ── Bước 2: Kiểm tra invitation thuộc về currentUser ──
        // Chỉ tantou được mời mới có quyền phản hồi
        if (!invitation.getTantou().getId().equals(currentUser.getUserId())) {
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

        // ── Bước 5: Nếu ACCEPTED → gán tantouEditor cho series ──
        Series series = invitation.getSeries();
        if (newStatus == InvitationStatus.ACCEPTED) {
            series.setTantouEditor(invitation.getTantou());
            seriesRepository.save(series);
        }

        // ── Bước 6: Cập nhật status + respondedAt ──
        invitation.setStatus(newStatus);
        invitation.setRespondedAt(LocalDateTime.now());

        SeriesTantouInvitation saved = seriesTantouInvitationRepository.save(invitation);

        // ── Bước 7: Map sang DTO ──
        SeriesTantouResponse response = seriesTantouMapper.toResponse(saved);

        // Push real-time cho MANGAKA biết tantou đã accept/reject lời mời
        Long mangakaId = invitation.getSeries().getMangaka().getId();
        String eventType = "TANTOU_INVITATION_" + newStatus.name();
        // VD: TANTOU_INVITATION_ACCEPTED / TANTOU_INVITATION_REJECTED
        webSocketService.sendToUser(mangakaId, eventType, response);

        // ── Bước 8: Trả về response ──
        return response;
    }

    // ════════════════════════════════════════════════════════════
    // 5. REMOVE TANTOU — MANGAKA xoá tantou khỏi series
    // ════════════════════════════════════════════════════════════

    /**
     * MANGAKA xoá/huỷ lời mời tantou khỏi series.
     * <p>
     * 📌 Luồng xử lý:
     *    1. Kiểm tra series tồn tại + currentUser có phải mangaka không
     *    2. Xoá record series_tantou_invitation (bất kể trạng thái nào)
     *       - ACCEPTED: xoá khỏi danh sách reviewer, clear tantouEditor
     *       - PENDING: huỷ lời mời
     *       - REJECTED: xoá lịch sử
     *
     * @param seriesId    ID của series
     * @param tantouId    ID của tantou cần xoá
     * @param currentUser User đang đăng nhập (MANGAKA)
     * @throws AppException 404 — nếu series không tồn tại hoặc không phải chủ
     * @throws AppException 404 — nếu không tìm thấy record series_tantou_invitation
     */
    @Transactional
    public void removeTantouInvitation(Long seriesId, Long tantouId, CustomUserDetails currentUser) {
        // ── Bước 1: Kiểm tra series tồn tại + ownership ──
        Series series = seriesRepository.findByIdAndMangakaId(seriesId, currentUser.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Series not found or not owned by you"));

        // ── Bước 2: Kiểm tra record tồn tại ──
        var existingOpt = seriesTantouInvitationRepository.findBySeriesIdAndTantouId(
                seriesId, tantouId);

        if (existingOpt.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND,
                    "Tantou is not associated with this series");
        }

        // ── Bước 3: Nếu tantou đang là tantouEditor của series → clear ──
        if (series.getTantouEditor() != null
                && series.getTantouEditor().getId().equals(tantouId)) {
            series.setTantouEditor(null);
            seriesRepository.save(series);
        }

        // ── Bước 4: Xoá record ──
        seriesTantouInvitationRepository.deleteBySeriesIdAndTantouId(seriesId, tantouId);
    }
}
