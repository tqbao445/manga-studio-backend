package com.mangaflow.studio.service.meeting;

import com.mangaflow.studio.common.exception.AppException;
import com.mangaflow.studio.common.security.CustomUserDetails;
import com.mangaflow.studio.dto.meeting.*;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.auth.VoteCriterion;
import com.mangaflow.studio.model.meeting.*;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.auth.VoteCriterionRepository;
import com.mangaflow.studio.repository.meeting.*;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ── MeetingService ──
 * Service xử lý toàn bộ logic liên quan đến cuộc họp phê duyệt series.
 *
 * Tại sao tách riêng MeetingService?
 * - Logic vote + meeting khá phức tạp, không nên nhồi vào SeriesWorkflowService.
 * - Dễ bảo trì: sửa quy trình họp không ảnh hưởng đến các service khác.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final SeriesMeetingRepository seriesMeetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SeriesVoteScoreRepository seriesVoteScoreRepository;
    private final VoteCriterionRepository voteCriterionRepository;
    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;

    // ════════════════════════════════════════════════════════════
    // 1. CREATE MEETING — Chief Editor tạo cuộc họp
    // ════════════════════════════════════════════════════════════

    /**
     * Chief Editor tạo cuộc họp mới cho một series.
     *
     * Luồng xử lý:
     * 1. Kiểm tra series tồn tại và đang ở PENDING_BOARD_VOTE.
     * 2. Kiểm tra user gọi có role CHIEF_EDITOR (đã được @PreAuthorize ở Controller).
     * 3. Tạo SeriesMeeting với status = PENDING.
     * 4. Thêm danh sách participant vào MeetingParticipant.
     *
     * Tại sao chỉ cho tạo khi PENDING_BOARD_VOTE?
     * - Tránh tạo meeting cho series chưa qua tantou duyệt.
     * - Tránh tạo meeting trùng cho series đã ONGOING/CANCELLED.
     */
    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, CustomUserDetails user) {
        Series series = seriesRepository.findById(request.getSeriesId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Series not found"));

        if (series.getStatus() != SeriesStatus.PENDING_BOARD_VOTE) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Series must be in PENDING_BOARD_VOTE status to create a meeting");
        }

        User creator = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Tạo cuộc họp
        SeriesMeeting meeting = SeriesMeeting.builder()
                .series(series)
                .title(request.getTitle())
                .description(request.getDescription())
                .meetingLink(request.getMeetingLink())
                .createdBy(creator)
                .status(MeetingStatus.PENDING)
                .startedAt(request.getStartedAt())
                .build();
        meeting = seriesMeetingRepository.save(meeting);

        // Tạo participant từ danh sách ID
        Long meetingId = meeting.getId();
        for (Long participantId : request.getParticipantIds()) {
            User participantUser = userRepository.findById(participantId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "User not found with id: " + participantId));

            MeetingParticipant mp = MeetingParticipant.builder()
                    .meeting(meeting)
                    .user(participantUser)
                    .build();
            meetingParticipantRepository.save(mp);
        }

        return buildMeetingResponse(meeting);
    }

    // ════════════════════════════════════════════════════════════
    // 2. GET MEETING — Lấy chi tiết cuộc họp
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy thông tin chi tiết của 1 cuộc họp.
     * Bao gồm:
     * - Thông tin cuộc họp (title, link, status, decision,...)
     * - Danh sách participant
     * - Kết quả vote (nếu có)
     */
    public MeetingResponse getMeeting(Long meetingId) {
        SeriesMeeting meeting = seriesMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Meeting not found"));
        return buildMeetingResponse(meeting);
    }

    // ════════════════════════════════════════════════════════════
    // 3. GET MEETINGS BY SERIES
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy tất cả cuộc họp của 1 series.
     * Dùng cho frontend hiển thị lịch sử họp.
     */
    public List<MeetingResponse> getMeetingsBySeries(Long seriesId) {
        List<SeriesMeeting> meetings = seriesMeetingRepository.findBySeriesIdOrderByCreatedAtDesc(seriesId);
        return meetings.stream()
                .map(this::buildMeetingResponse)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // 3b. GET MEETINGS FOR USER — User xem danh sách meetings của mình
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy tất cả cuộc họp mà user hiện tại được mời tham gia.
     * Dùng cho EditorialBoardPage hiển thị danh sách meeting.
     *
     * Cách hoạt động:
     * 1. Query MeetingParticipant by userId → lấy các meeting IDs
     * 2. Với mỗi meeting, build MeetingResponse đầy đủ
     * 3. Sắp xếp mới nhất trước
     *
     * @param userId ID của user cần lấy meetings
     * @return Danh sách MeetingResponse đã sắp xếp
     */
    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetingsForUser(Long userId) {
        // Tìm tất cả participant records của user này (JOIN FETCH meeting)
        List<MeetingParticipant> participants = meetingParticipantRepository.findByUserId(userId);

        // Build response cho từng meeting, sắp xếp mới nhất trước
        return participants.stream()
                .map(mp -> buildMeetingResponse(mp.getMeeting()))
                .sorted((a, b) -> {
                    // Nếu có createdAt thì so sánh, không thì mặc định
                    if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // 3c. GET ACTIVE CRITERIA — Lấy danh sách tiêu chí chấm điểm
    // ════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách tiêu chí chấm điểm đang active (is_active = true).
     * Frontend dùng để render form vote (ScoreSlider).
     *
     * Cách hoạt động:
     * 1. Gọi VoteCriterionRepository.findByIsActiveTrueOrderBySortOrderAsc()
     * 2. Map entity → CriterionResponse DTO
     *
     * Tại sao không trả entity trực tiếp?
     * - Entity có thể bị lazy loading exception nếu session đóng
     * - DTO chỉ expose đúng field cần thiết, không lộ internal state
     *
     * @return Danh sách CriterionResponse đã sắp xếp theo sortOrder
     */
    @Transactional(readOnly = true)
    public List<CriterionResponse> getActiveCriteria() {
        return voteCriterionRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(c -> CriterionResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .weight(c.getWeight())
                        .sortOrder(c.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // 4. CAST VOTE — EDITORIAL_BOARD bỏ phiếu
    // ════════════════════════════════════════════════════════════

    /**
     * EDITORIAL_BOARD member bỏ phiếu cho 1 cuộc họp.
     *
     * Luồng xử lý:
     * 1. Kiểm tra meeting tồn tại và đang PENDING/IN_PROGRESS.
     * 2. Kiểm tra user có phải participant của meeting không.
     * 3. Upsert vote: nếu đã vote thì UPDATE (cho phép đổi ý).
     * 4. Xoá scores cũ, thêm scores mới.
     * 5. Trả về kết quả vote hiện tại.
     *
     * Tại sao dùng upsert?
     * - Cho phép EDITORIAL_BOARD đổi ý trước khi Chief ra quyết định.
     * - Đơn giản: xoá cũ → insert mới thay vì update từng cái.
     */
    @Transactional
    public VoteResponse castVote(Long meetingId, VoteRequest request, CustomUserDetails user) {
        SeriesMeeting meeting = seriesMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Meeting not found"));

        if (meeting.getStatus() == MeetingStatus.COMPLETED || meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Meeting is already closed");
        }

        // Kiểm tra user có được mời không
        boolean isParticipant = meetingParticipantRepository.existsByMeetingIdAndUserId(meetingId, user.getUserId());
        if (!isParticipant) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a participant of this meeting");
        }

        User voter = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Upsert: tìm vote cũ, nếu có thì cập nhật, nếu chưa thì tạo mới
        SeriesVote vote = seriesVoteRepository.findByMeetingIdAndVoterId(meetingId, user.getUserId())
                .orElseGet(() -> SeriesVote.builder()
                        .meeting(meeting)
                        .voter(voter)
                        .build());

        vote.setVote(request.getVote());
        vote.setComment(request.getComment());
        vote.setVotedAt(LocalDateTime.now());
        vote = seriesVoteRepository.save(vote);

        // Xoá scores cũ, thêm scores mới
        List<SeriesVoteScore> oldScores = seriesVoteScoreRepository.findByVoteId(vote.getId());
        if (!oldScores.isEmpty()) {
            seriesVoteScoreRepository.deleteAll(oldScores);
        }

        // Validate criterion IDs tồn tại
        Set<Long> criterionIds = request.getScores().stream()
                .map(VoteRequest.CriterionScore::getCriterionId)
                .collect(Collectors.toSet());
        List<VoteCriterion> validCriteria = voteCriterionRepository.findAllById(criterionIds);
        if (validCriteria.size() != criterionIds.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "One or more criterion IDs are invalid");
        }

        // Lưu scores mới
        for (VoteRequest.CriterionScore cs : request.getScores()) {
            VoteCriterion criterion = voteCriterionRepository.findById(cs.getCriterionId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Criterion not found: " + cs.getCriterionId()));

            SeriesVoteScore score = SeriesVoteScore.builder()
                    .vote(vote)
                    .criterion(criterion)
                    .score(cs.getScore())
                    .build();
            seriesVoteScoreRepository.save(score);
        }

        // ═══ Auto-complete: nếu tất cả EDITORIAL_BOARD đã vote → COMPLETED ═══
        // Logic:
        // 1. Đếm tổng số EDITORIAL_BOARD được mời tham gia meeting này
        // 2. Đếm số EDITORIAL_BOARD đã bỏ phiếu
        // 3. Nếu bằng nhau → tất cả đã vote → tự động kết thúc meeting
        // 4. Chỉ auto-complete khi meeting chưa COMPLETED (tránh set lại)
        if (meeting.getStatus() != MeetingStatus.COMPLETED) {
            long totalBoardMembers = meetingParticipantRepository.countBoardMembersByMeetingId(meetingId);
            long votedBoardMembers = seriesVoteRepository.countBoardVotersByMeetingId(meetingId);

            if (totalBoardMembers > 0 && votedBoardMembers >= totalBoardMembers) {
                meeting.setStatus(MeetingStatus.COMPLETED);
                meeting.setEndedAt(LocalDateTime.now());
                seriesMeetingRepository.save(meeting);
            }
        }

        return buildVoteResponse(meeting, user.getUserId());
    }

    // ════════════════════════════════════════════════════════════
    // 5. GET VOTE RESULTS — Xem kết quả vote
    // ════════════════════════════════════════════════════════════

    /**
     * Xem kết quả vote hiện tại của 1 cuộc họp.
     * Không thay đổi gì — chỉ đọc dữ liệu.
     */
    public VoteResponse getVoteResults(Long meetingId, Long currentUserId) {
        SeriesMeeting meeting = seriesMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Meeting not found"));
        return buildVoteResponse(meeting, currentUserId);
    }

    // ════════════════════════════════════════════════════════════
    // 6. MAKE DECISION — Chief Editor quyết định cuối
    // ════════════════════════════════════════════════════════════

    /**
     * Chief Editor ra quyết định cuối cùng cho series.
     *
     * Luồng xử lý:
     * 1. Kiểm tra meeting tồn tại.
     * 2. Kiểm tra user gọi là CHIEF_EDITOR (đã @PreAuthorize).
     * 3. Kiểm tra chưa có decision nào (không cho sửa decision).
     * 4. Nếu APPROVED:
     *    - series.status = ONGOING
     *    - meeting.decision = APPROVED
     * 5. Nếu REJECTED:
     *    - series.status = DRAFT
     *    - meeting.decision = REJECTED
     * 6. meeting.status = COMPLETED
     * 7. meeting.endedAt = now
     *
     * Tại sao không cho sửa decision?
     * - Tránh trường hợp Chief đổi ý sau khi đã công bố.
     * - Nếu muốn thay đổi, Chief phải tạo meeting mới.
     */
    @Transactional
    public MeetingResponse makeDecision(Long meetingId, DecisionRequest request, CustomUserDetails user) {
        SeriesMeeting meeting = seriesMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Meeting not found"));

        if (meeting.getDecision() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "This meeting already has a decision: " + meeting.getDecision());
        }

        String decision = request.getDecision().toUpperCase();
        if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVED or REJECTED");
        }

        Series series = meeting.getSeries();
        if (decision.equals("APPROVED")) {
            series.setStatus(SeriesStatus.ONGOING);
            meeting.setDecision("APPROVED");
        } else {
            series.setStatus(SeriesStatus.DRAFT);
            meeting.setDecision("REJECTED");
        }

        meeting.setStatus(MeetingStatus.COMPLETED);
        meeting.setEndedAt(LocalDateTime.now());

        seriesRepository.save(series);
        meeting = seriesMeetingRepository.save(meeting);

        return buildMeetingResponse(meeting);
    }

    // ════════════════════════════════════════════════════════════
    // PRIVATE HELPERS — Build response objects
    // ════════════════════════════════════════════════════════════

    /**
     * Tạo MeetingResponse đầy đủ từ entity SeriesMeeting.
     * Bao gồm:
     * - Thông tin cơ bản (id, title, link, status,...)
     * - Danh sách participant (id + tên)
     * - Kết quả vote (nếu có)
     */
    private MeetingResponse buildMeetingResponse(SeriesMeeting meeting) {
        List<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingId(meeting.getId());

        List<MeetingResponse.ParticipantInfo> participantInfos = participants.stream()
                .map(mp -> MeetingResponse.ParticipantInfo.builder()
                        .userId(mp.getUser().getId())
                        .username(mp.getUser().getUsername())
                        .displayName(mp.getUser().getDisplayName())
                        .build())
                .collect(Collectors.toList());

        List<SeriesVote> votes = seriesVoteRepository.findByMeetingId(meeting.getId());

        MeetingResponse.VoteSummary voteSummary = null;
        if (!votes.isEmpty()) {
            long yesCount = votes.stream().filter(v -> v.getVote() == VoteType.YES).count();
            long noCount = votes.stream().filter(v -> v.getVote() == VoteType.NO).count();

            List<MeetingResponse.VoteSummary.VoteDetail> details = votes.stream()
                    .map(v -> {
                        List<SeriesVoteScore> scores = seriesVoteScoreRepository.findByVoteId(v.getId());
                        List<MeetingResponse.VoteSummary.VoteDetail.ScoreDetail> scoreDetails = scores.stream()
                                .map(s -> MeetingResponse.VoteSummary.VoteDetail.ScoreDetail.builder()
                                        .criterionName(s.getCriterion().getName())
                                        .score(s.getScore())
                                        .build())
                                .collect(Collectors.toList());

                        return MeetingResponse.VoteSummary.VoteDetail.builder()
                                .voterId(v.getVoter().getId())
                                .voterName(v.getVoter().getDisplayName() != null
                                        ? v.getVoter().getDisplayName() : v.getVoter().getUsername())
                                .vote(v.getVote().name())
                                .comment(v.getComment())
                                .scores(scoreDetails)
                                .build();
                    })
                    .collect(Collectors.toList());

            long totalBoardMembers = meetingParticipantRepository.countBoardMembersByMeetingId(meeting.getId());

            voteSummary = MeetingResponse.VoteSummary.builder()
                    .totalVotes(votes.size())
                    .totalBoardMembers(totalBoardMembers)
                    .yesCount(yesCount)
                    .noCount(noCount)
                    .details(details)
                    .build();
        }

        return MeetingResponse.builder()
                .id(meeting.getId())
                .seriesId(meeting.getSeries().getId())
                .seriesTitle(meeting.getSeries().getTitle())
                .seriesCoverImageUrl(meeting.getSeries().getCoverImageUrl())
                .seriesCoverColor(meeting.getSeries().getCoverColor())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .meetingLink(meeting.getMeetingLink())
                .createdById(meeting.getCreatedBy().getId())
                .createdByName(meeting.getCreatedBy().getDisplayName() != null
                        ? meeting.getCreatedBy().getDisplayName() : meeting.getCreatedBy().getUsername())
                .status(meeting.getStatus())
                .startedAt(meeting.getStartedAt())
                .endedAt(meeting.getEndedAt())
                .decision(meeting.getDecision())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .participants(participantInfos)
                .voteSummary(voteSummary)
                .build();
    }

    /**
     * Tạo VoteResponse từ entity SeriesMeeting.
     * Bao gồm vote của user hiện tại + tổng hợp phiếu.
     */
    private VoteResponse buildVoteResponse(SeriesMeeting meeting, Long currentUserId) {
        long yesCount = seriesVoteRepository.countByMeetingIdAndVote(meeting.getId(), VoteType.YES);
        long noCount = seriesVoteRepository.countByMeetingIdAndVote(meeting.getId(), VoteType.NO);

        long totalBoardMembers = meetingParticipantRepository.countBoardMembersByMeetingId(meeting.getId());

        // Lấy vote của user hiện tại
        VoteType myVote = null;
        String myComment = null;
        List<VoteResponse.CriterionScoreResponse> myScores = new ArrayList<>();

        java.util.Optional<SeriesVote> userVote = seriesVoteRepository
                .findByMeetingIdAndVoterId(meeting.getId(), currentUserId);
        if (userVote.isPresent()) {
            myVote = userVote.get().getVote();
            myComment = userVote.get().getComment();

            List<SeriesVoteScore> scores = seriesVoteScoreRepository.findByVoteId(userVote.get().getId());
            myScores = scores.stream()
                    .map(s -> VoteResponse.CriterionScoreResponse.builder()
                            .criterionId(s.getCriterion().getId())
                            .criterionName(s.getCriterion().getName())
                            .score(s.getScore())
                            .build())
                    .collect(Collectors.toList());
        }

        return VoteResponse.builder()
                .meetingId(meeting.getId())
                .seriesId(meeting.getSeries().getId())
                .myVote(myVote)
                .myComment(myComment)
                .myScores(myScores)
                .voteCountYes(yesCount)
                .voteCountNo(noCount)
                .totalBoardMembers(totalBoardMembers)
                .currentDecision(meeting.getDecision())
                .build();
    }
}
