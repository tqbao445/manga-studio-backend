package com.mangaflow.studio.data;

import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.auth.VoteCriterion;
import com.mangaflow.studio.model.meeting.MeetingParticipant;
import com.mangaflow.studio.model.meeting.MeetingStatus;
import com.mangaflow.studio.model.meeting.SeriesMeeting;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.auth.VoteCriterionRepository;
import com.mangaflow.studio.repository.meeting.MeetingParticipantRepository;
import com.mangaflow.studio.repository.meeting.SeriesMeetingRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VoteCriterionRepository voteCriterionRepository;
    private final SeriesRepository seriesRepository;
    private final SeriesMeetingRepository seriesMeetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;

    @Override
    public void run(String... args) {
        seedUsers();
        seedVoteCriteria();
        seedSeriesAndMeeting();
    }

    private void seedUsers() {
        List<User> toSave = new ArrayList<>();

        addIfMissing("ichikawa@manga.com", "ichikawa", "Ichikawa", Role.MANGAKA, toSave);
        addIfMissing("tanaka@manga.com", "tanaka", "Tanaka", Role.ASSISTANT, toSave);
        addIfMissing("suzuki@manga.com", "suzuki", "Suzuki", Role.ASSISTANT, toSave);
        addIfMissing("yamamoto@manga.com", "yamamoto", "Yamamoto", Role.ASSISTANT, toSave);
        addIfMissing("sato@editor.com", "sato_editor", "Sato", Role.TANTOU_EDITOR, toSave);
        addIfMissing("taniguchi@editor.com", "taniguchi", "Taniguchi", Role.TANTOU_EDITOR, toSave);
        addIfMissing("kimura@board.com", "kimura", "Kimura", Role.EDITORIAL_BOARD, toSave);
        addIfMissing("nishida@board.com", "nishida", "Nishida", Role.EDITORIAL_BOARD, toSave);
        addIfMissing("fujimoto@manga.com", "fujimoto", "Fujimoto", Role.MANGAKA, toSave);
        addIfMissing("ito@manga.com", "ito", "Ito", Role.MANGAKA, toSave);
        addIfMissing("chief@editor.com", "chief", "Takahashi", Role.CHIEF_EDITOR, toSave);

        if (!toSave.isEmpty()) {
            toSave.forEach(u -> u.setPassword(passwordEncoder.encode("password")));
            userRepository.saveAll(toSave);
            System.out.println("Seeded " + toSave.size() + " users");
        } else {
            System.out.println("Users already exist, skipping");
        }
    }

    private void addIfMissing(String email, String username, String displayName, Role role, List<User> list) {
        if (userRepository.findByEmail(email).isEmpty()) {
            list.add(User.builder()
                    .email(email)
                    .username(username)
                    .displayName(displayName)
                    .role(role)
                    .build());
        }
    }

    private void seedVoteCriteria() {
        if (voteCriterionRepository.count() > 0) {
            System.out.println("Vote criteria already exist, skipping");
            return;
        }

        List<VoteCriterion> criteria = List.of(
                VoteCriterion.builder()
                        .name("Nội dung")
                        .description("Đánh giá nội dung cốt truyện, kịch bản, ý tưởng")
                        .weight(3)
                        .sortOrder(1)
                        .isActive(true)
                        .build(),
                VoteCriterion.builder()
                        .name("Vẽ")
                        .description("Đánh giá chất lượng nét vẽ, panel, bố cục")
                        .weight(2)
                        .sortOrder(2)
                        .isActive(true)
                        .build(),
                VoteCriterion.builder()
                        .name("Sáng tạo")
                        .description("Đánh giá tính sáng tạo, khác biệt so với tác phẩm khác")
                        .weight(1)
                        .sortOrder(3)
                        .isActive(true)
                        .build()
        );

        voteCriterionRepository.saveAll(criteria);
        System.out.println("Seeded " + criteria.size() + " vote criteria");
    }

    private void seedSeriesAndMeeting() {
        if (seriesRepository.count() > 0) {
            System.out.println("Series already exist, skipping");
            return;
        }

        User ichikawa = userRepository.findByEmail("ichikawa@manga.com").orElseThrow();
        User sato = userRepository.findByEmail("sato@editor.com").orElseThrow();

        Series series = Series.builder()
                .title("Blade of the Samurai")
                .titleJp("侍の刃")
                .synopsis("Một samurai lang thang tìm kiếm thanh kiếm huyền thoại để bảo vệ vương quốc.")
                .genres(List.of(Genre.ACTION))
                .targetDemographics(List.of(TargetDemographic.SHONEN))
                .status(SeriesStatus.PENDING_BOARD_VOTE)
                .coverColor("#FF4500")
                .mangaka(ichikawa)
                .tantouEditor(sato)
                .chapterCount(0)
                .build();

        seriesRepository.save(series);
        System.out.println("Seeded 1 series");

        User chief = userRepository.findByEmail("chief@editor.com").orElseThrow();

        SeriesMeeting meeting = SeriesMeeting.builder()
                .series(series)
                .title("Phê duyệt Blade of the Samurai")
                .description("Cuộc họp phê duyệt series mới của Ichikawa-sensei.")
                .meetingLink("https://meet.google.com/abc-defg-hij")
                .createdBy(chief)
                .status(MeetingStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        seriesMeetingRepository.save(meeting);
        System.out.println("Seeded 1 meeting");

        User kimura = userRepository.findByEmail("kimura@board.com").orElseThrow();
        User nishida = userRepository.findByEmail("nishida@board.com").orElseThrow();

        List<MeetingParticipant> participants = List.of(
                MeetingParticipant.builder().meeting(meeting).user(kimura).build(),
                MeetingParticipant.builder().meeting(meeting).user(nishida).build(),
                MeetingParticipant.builder().meeting(meeting).user(sato).build()
        );

        meetingParticipantRepository.saveAll(participants);
        System.out.println("Seeded 3 meeting participants");
    }
}
