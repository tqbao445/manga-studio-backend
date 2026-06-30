package com.mangaflow.studio.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.Character;
import com.mangaflow.studio.model.series.RoadmapArc;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.model.auth.VoteCriterion;
import com.mangaflow.studio.model.meeting.MeetingParticipant;
import com.mangaflow.studio.model.meeting.MeetingStatus;
import com.mangaflow.studio.model.meeting.SeriesMeeting;
import com.mangaflow.studio.model.meeting.SeriesVote;
import com.mangaflow.studio.model.meeting.SeriesVoteScore;
import com.mangaflow.studio.model.meeting.VoteType;
import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import com.mangaflow.studio.model.schedule.PublicationSchedule;
import com.mangaflow.studio.model.schedule.ScheduleType;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.auth.VoteCriterionRepository;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.meeting.MeetingParticipantRepository;
import com.mangaflow.studio.repository.meeting.SeriesMeetingRepository;
import com.mangaflow.studio.repository.meeting.SeriesVoteRepository;
import com.mangaflow.studio.repository.meeting.SeriesVoteScoreRepository;
import com.mangaflow.studio.repository.metric.SeriesPeriodMetricRepository;
import com.mangaflow.studio.repository.schedule.PublicationScheduleRepository;
import com.mangaflow.studio.repository.series.CharacterRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final PublicationScheduleRepository publicationScheduleRepository;
    private final VoteCriterionRepository voteCriterionRepository;
    private final SeriesMeetingRepository seriesMeetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final SeriesVoteRepository seriesVoteRepository;
    private final SeriesVoteScoreRepository seriesVoteScoreRepository;
    private final SeriesPeriodMetricRepository seriesPeriodMetricRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedUsers();
        seedVoteCriteria();
        seedSeries();
        seedChapters();
        seedCharacters();
        seedSchedules();
        seedSeriesPeriodMetrics();
        seedMeetings();
    }

    private void seedUsers() throws Exception {
        if (userRepository.count() > 0) {
            System.out.println("Users already exist, skipping");
            return;
        }

        var input = new ClassPathResource("data/users.json").getInputStream();
        List<UserSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<User> users = seeds.stream()
                .map(s -> User.builder()
                        .email(s.email)
                        .username(s.username)
                        .displayName(s.displayName)
                        .role(Role.valueOf(s.role))
                        .password(passwordEncoder.encode(s.password))
                        .build())
                .toList();

        userRepository.saveAll(users);
        System.out.println("Seeded " + users.size() + " users");
    }

    private void seedVoteCriteria() throws Exception {
        if (voteCriterionRepository.count() > 0) {
            System.out.println("Vote criteria already exist, skipping");
            return;
        }

        var input = new ClassPathResource("data/vote-criteria.json").getInputStream();
        List<VoteCriterionSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<VoteCriterion> criteria = seeds.stream()
                .map(s -> VoteCriterion.builder()
                        .name(s.name)
                        .description(s.description)
                        .weight(s.weight)
                        .sortOrder(s.sortOrder)
                        .isActive(true)
                        .build())
                .toList();

        voteCriterionRepository.saveAll(criteria);
        System.out.println("Seeded " + criteria.size() + " vote criteria");
    }

    private void seedSeries() throws Exception {
        if (seriesRepository.count() > 0) {
            System.out.println("Series already exist, skipping");
            return;
        }

        var input = new ClassPathResource("data/series.json").getInputStream();
        List<SeriesSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<Series> seriesList = seeds.stream().map(s -> {
            User mangaka = userRepository.findByEmail(s.mangakaEmail).orElseThrow();
            User tantou = userRepository.findByEmail(s.tantouEditorEmail).orElseThrow();

            List<RoadmapArc> roadmap = s.storyRoadmap == null ? new ArrayList<>() :
                    new ArrayList<>(s.storyRoadmap.stream()
                            .map(r -> RoadmapArc.builder().title(r.title).summary(r.summary).build())
                            .toList());

            return Series.builder()
                    .title(s.title)
                    .titleJp(s.titleJp)
                    .synopsis(s.synopsis)
                    .genres(new ArrayList<>(s.genres.stream().map(Genre::valueOf).toList()))
                    .targetDemographics(new ArrayList<>(s.targetDemographics.stream().map(TargetDemographic::valueOf).toList()))
                    .coverColor(s.coverColor)
                    .coverImageUrl(s.coverImageUrl)
                    .mangaka(mangaka)
                    .tantouEditor(tantou)
                    .worldLoreContent(s.worldLoreContent)
                    .storyRoadmap(roadmap)
                    .status(SeriesStatus.ONGOING)
                    .chapterCount(0)
                    .build();
        }).toList();

        seriesRepository.saveAll(seriesList);
        System.out.println("Seeded " + seriesList.size() + " series");
    }

    private void seedChapters() throws Exception {
        if (chapterRepository.count() > 0) {
            System.out.println("Chapters already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = seriesRepository.findAll().stream()
                .collect(Collectors.toMap(Series::getTitle, s -> s));

        var input = new ClassPathResource("data/chapters.json").getInputStream();
        List<ChapterSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<Chapter> allChapters = new ArrayList<>();
        for (ChapterSeed seed : seeds) {
            Series series = seriesMap.get(seed.seriesTitle);
            if (series == null) {
                System.out.println("Series not found: " + seed.seriesTitle);
                continue;
            }

            LocalDate firstDate = LocalDate.parse(seed.firstPublishDate);
            LocalDate w28Start = LocalDate.of(2026, 7, 6);
            List<Chapter> chapters = seed.chapters.stream()
                    .map(c -> {
                        LocalDateTime publishDate = firstDate.plusWeeks(c.chapterNumber - 1).atStartOfDay();
                        ChapterStatus status = publishDate.toLocalDate().isBefore(w28Start)
                                ? ChapterStatus.PUBLISHED : ChapterStatus.APPROVED;
                        return Chapter.builder()
                                .series(series)
                                .chapterNumber(c.chapterNumber)
                                .title(c.title)
                                .pageCount(c.pageCount)
                                .progressPercent(100)
                                .publishDate(publishDate)
                                .status(status)
                                .build();
                    })
                    .toList();

            allChapters.addAll(chapters);
            series.setChapterCount(chapters.size());
        }

        chapterRepository.saveAll(allChapters);
        System.out.println("Seeded " + allChapters.size() + " chapters");
    }

    private void seedCharacters() throws Exception {
        if (characterRepository.count() > 0) {
            System.out.println("Characters already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = seriesRepository.findAll().stream()
                .collect(Collectors.toMap(Series::getTitle, s -> s));

        var input = new ClassPathResource("data/characters.json").getInputStream();
        List<CharacterSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<Character> allChars = new ArrayList<>();
        for (CharacterSeed seed : seeds) {
            Series series = seriesMap.get(seed.seriesTitle);
            if (series == null) {
                System.out.println("Series not found: " + seed.seriesTitle);
                continue;
            }

            List<Character> chars = seed.characters.stream()
                    .map(c -> Character.builder()
                            .series(series)
                            .name(c.name)
                            .motivation(c.motivation)
                            .build())
                    .toList();

            allChars.addAll(chars);
        }

        characterRepository.saveAll(allChars);
        System.out.println("Seeded " + allChars.size() + " characters");
    }

    private void seedSchedules() throws Exception {
        if (publicationScheduleRepository.count() > 0) {
            System.out.println("Publication schedules already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = seriesRepository.findAll().stream()
                .collect(Collectors.toMap(Series::getTitle, s -> s));

        var input = new ClassPathResource("data/schedules.json").getInputStream();
        List<ScheduleSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        List<PublicationSchedule> schedules = seeds.stream().map(s -> {
            Series series = seriesMap.get(s.seriesTitle);
            if (series == null) {
                System.out.println("Series not found: " + s.seriesTitle);
                return null;
            }
            return PublicationSchedule.builder()
                    .series(series)
                    .scheduleType(ScheduleType.valueOf(s.scheduleType))
                    .dayOfWeek(s.dayOfWeek)
                    .dayOfMonth(s.dayOfMonth)
                    .startDate(LocalDate.parse(s.startDate))
                    .build();
        }).filter(java.util.Objects::nonNull).toList();

        publicationScheduleRepository.saveAll(schedules);
        System.out.println("Seeded " + schedules.size() + " publication schedules");
    }

    private void seedSeriesPeriodMetrics() {
        if (seriesPeriodMetricRepository.count() > 0) {
            System.out.println("Series period metrics already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = seriesRepository.findAll().stream()
                .collect(Collectors.toMap(Series::getTitle, s -> s));
        List<PublicationSchedule> schedules = publicationScheduleRepository.findAll();

        Map<String, ScheduleType> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(s -> s.getSeries().getTitle(), PublicationSchedule::getScheduleType));

        List<String> weeklyLabels = new ArrayList<>();
        for (int w = 15; w <= 27; w++) {
            weeklyLabels.add(String.format("2026-W%02d", w));
        }

        List<String> monthlyLabels = new ArrayList<>();
        for (int m = 4; m <= 6; m++) {
            monthlyLabels.add(String.format("2026-%02d", m));
        }

        List<SeriesPeriodMetric> allMetrics = new ArrayList<>();
        for (Map.Entry<String, ScheduleType> entry : scheduleMap.entrySet()) {
            Series series = seriesMap.get(entry.getKey());
            if (series == null) continue;

            List<String> labels = entry.getValue() == ScheduleType.WEEKLY ? weeklyLabels : monthlyLabels;
            String periodType = entry.getValue().name();

            for (String label : labels) {
                long totalVotes = 50 + (long) (Math.random() * 451);
                double avgScore = Math.round((5.5 + Math.random() * 4.0) * 100.0) / 100.0;
                double score = Math.round((totalVotes * 0.7 + avgScore * 100) * 100.0) / 100.0;

                allMetrics.add(SeriesPeriodMetric.builder()
                        .series(series)
                        .periodLabel(label)
                        .periodType(periodType)
                        .totalVotes(totalVotes)
                        .avgScore(avgScore)
                        .score(score)
                        .build());
            }
        }

        Map<String, List<SeriesPeriodMetric>> byPeriod = allMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getPeriodLabel() + "|" + m.getPeriodType()));
        for (List<SeriesPeriodMetric> periodMetrics : byPeriod.values()) {
            periodMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            for (int i = 0; i < periodMetrics.size(); i++) {
                periodMetrics.get(i).setRank(i + 1);
            }
        }

        seriesPeriodMetricRepository.saveAll(allMetrics);
        System.out.println("Seeded " + allMetrics.size() + " series period metrics");
    }

    private void seedMeetings() throws Exception {
        if (seriesMeetingRepository.count() > 0) {
            System.out.println("Meetings already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = seriesRepository.findAll().stream()
                .collect(Collectors.toMap(Series::getTitle, s -> s));
        List<VoteCriterion> allCriteria = voteCriterionRepository.findAll();

        var input = new ClassPathResource("data/meetings.json").getInputStream();
        List<MeetingSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {});

        for (MeetingSeed seed : seeds) {
            Series series = seriesMap.get(seed.seriesTitle);
            User chief = userRepository.findByEmail(seed.createdByEmail).orElseThrow();
            if (series == null) {
                System.out.println("Series not found: " + seed.seriesTitle);
                continue;
            }

            SeriesMeeting meeting = SeriesMeeting.builder()
                    .series(series)
                    .title(seed.title)
                    .description(seed.description)
                    .meetingLink(seed.meetingLink)
                    .createdBy(chief)
                    .status(MeetingStatus.valueOf(seed.status))
                    .decision(seed.decision)
                    .build();
            seriesMeetingRepository.save(meeting);

            List<MeetingParticipant> participants = seed.participantEmails.stream()
                    .map(email -> {
                        User user = userRepository.findByEmail(email).orElseThrow();
                        return MeetingParticipant.builder()
                                .meeting(meeting)
                                .user(user)
                                .build();
                    }).toList();
            meetingParticipantRepository.saveAll(participants);

            if ("COMPLETED".equals(seed.status)) {
                List<String> comments = List.of(
                        "Solid work, approved.",
                        "Good potential, happy to approve.",
                        "Quality meets expectations."
                );

                for (MeetingParticipant p : participants) {
                    if (p.getUser().getRole() != com.mangaflow.studio.model.auth.Role.EDITORIAL_BOARD) {
                        continue;
                    }

                    SeriesVote vote = SeriesVote.builder()
                            .meeting(meeting)
                            .voter(p.getUser())
                            .vote(VoteType.YES)
                            .comment(comments.get((int) (Math.random() * comments.size())))
                            .build();
                    seriesVoteRepository.save(vote);

                    List<SeriesVoteScore> scores = allCriteria.stream()
                            .map(c -> SeriesVoteScore.builder()
                                    .vote(vote)
                                    .criterion(c)
                                    .score(7 + (int) (Math.random() * 3))
                                    .build())
                            .toList();
                    seriesVoteScoreRepository.saveAll(scores);
                }
            }
        }

        System.out.println("Seeded " + seeds.size() + " meetings");
    }

    private record UserSeed(String email, String username, String displayName, String role, String password) {}

    private record SeriesSeed(
            String title, String titleJp, String synopsis,
            List<String> genres, List<String> targetDemographics,
            String coverColor, String coverImageUrl,
            String mangakaEmail, String tantouEditorEmail,
            String worldLoreContent, List<RoadmapArcSeed> storyRoadmap
    ) {}

    private record RoadmapArcSeed(String title, String summary) {}

    private record ChapterSeed(String seriesTitle, String firstPublishDate, List<ChapterItem> chapters) {}

    private record ChapterItem(int chapterNumber, String title, int pageCount) {}

    private record CharacterSeed(String seriesTitle, List<CharacterItem> characters) {}

    private record CharacterItem(String name, String motivation) {}

    private record ScheduleSeed(
            String seriesTitle, String scheduleType,
            Integer dayOfWeek, Integer dayOfMonth, String startDate
    ) {}

    private record VoteCriterionSeed(String name, String description, Integer weight, Integer sortOrder) {}

    private record MeetingSeed(
            String seriesTitle, String title, String description,
            String meetingLink, String createdByEmail,
            String status, String decision,
            List<String> participantEmails
    ) {}
}
