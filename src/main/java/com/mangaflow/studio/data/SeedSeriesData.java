package com.mangaflow.studio.data;

import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.chapter.Chapter;
import com.mangaflow.studio.model.chapter.ChapterStatus;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.repository.chapter.ChapterRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import com.mangaflow.studio.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
public class SeedSeriesData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;

    @Override
    public void run(String... args) {
        if (seriesRepository.findAll().stream()
                .anyMatch(s -> "Shadow Monarch".equals(s.getTitle()))) {
            System.out.println("Series seed data already exists, skipping");
            return;
        }

        User ichikawa = userRepository.findByEmail("ichikawa@manga.com").orElseThrow();
        User fujimoto = userRepository.findByEmail("fujimoto@manga.com").orElseThrow();
        User ito = userRepository.findByEmail("ito@manga.com").orElseThrow();
        User sato = userRepository.findByEmail("sato@editor.com").orElseThrow();
        User taniguchi = userRepository.findByEmail("taniguchi@editor.com").orElseThrow();

        // ── Step 1: Blade of the Samurai → ONGOING ──
        Series blade = seriesRepository.findAll().stream()
                .filter(s -> "Blade of the Samurai".equals(s.getTitle()))
                .findFirst().orElse(null);
        if (blade != null) {
            blade.setStatus(SeriesStatus.ONGOING);
            seriesRepository.save(blade);
        }

        // ── Step 2: Define 7 series (including Blade) ──
        List<SeriesSeed> seeds = List.of(
                new SeriesSeed("Shadow Monarch", "シャドウ・モナーク",
                        "In a world where hunters fight monsters in dungeons, the weakest hunter Sung Jin-Woo gains a mysterious power that allows him to level up indefinitely, becoming the one and only Shadow Monarch.",
                        Genre.FANTASY, TargetDemographic.SHONEN,
                        ichikawa, sato, "#6B21A8", LocalDate.of(2026, 4, 6)),

                new SeriesSeed("Neon Horizon", "ネオン・ホライズン",
                        "In the neon-lit streets of Neo-Tokyo 2099, a young hacker discovers a conspiracy that threatens to erase the digital consciousness of millions trapped in a virtual world.",
                        Genre.ACTION, TargetDemographic.SHONEN,
                        fujimoto, taniguchi, "#DB2777", LocalDate.of(2026, 4, 7)),

                new SeriesSeed("Echoes of Eternity", "エコーズ・オブ・エタニティ",
                        "A time-traveling historian jumps between eras to prevent catastrophic events, but each jump fragments her memory and her sense of self.",
                        Genre.DRAMA, TargetDemographic.SEINEN,
                        ito, sato, "#7C3AED", LocalDate.of(2026, 4, 8)),

                new SeriesSeed("Blade of the Samurai", "侍の刃",
                        "A wandering ronin searches for a legendary sword to protect the kingdom from an ancient evil that threatens to plunge the land into eternal darkness.",
                        Genre.ACTION, TargetDemographic.SHONEN,
                        ichikawa, sato, "#DC2626", LocalDate.of(2026, 4, 9)),

                new SeriesSeed("Cherry Blossoms After Winter", "冬の後の桜",
                        "Two childhood friends separated by fate reunite years later, navigating love, loss, and the fleeting beauty of cherry blossoms.",
                        Genre.ROMANCE, TargetDemographic.SHOJO,
                        ichikawa, taniguchi, "#F43F5E", LocalDate.of(2026, 4, 10)),

                new SeriesSeed("Stardust Chronicles", "スターダスト・クロニクル",
                        "An interstellar explorer charts unknown galaxies, uncovering ancient alien civilizations and a cosmic threat that could erase all of existence.",
                        Genre.DRAMA, TargetDemographic.SEINEN,
                        ito, taniguchi, "#0891B2", LocalDate.of(2026, 4, 11)),

                new SeriesSeed("The Last Summoner", "最後の召喚士",
                        "In a world where summoners have been hunted to near extinction, the last surviving summoner must master forbidden magic to save the kingdom that betrayed her kind.",
                        Genre.FANTASY, TargetDemographic.SHONEN,
                        fujimoto, sato, "#15803D", LocalDate.of(2026, 4, 12))
        );

        // ── Step 3: Create series + chapters ──
        List<Series> savedSeries = new ArrayList<>();
        for (SeriesSeed seed : seeds) {
            Series series;
            if ("Blade of the Samurai".equals(seed.title)) {
                series = blade;
            } else {
                series = Series.builder()
                        .title(seed.title)
                        .titleJp(seed.titleJp)
                        .synopsis(seed.synopsis)
                        .genres(List.of(seed.genre))
                        .targetDemographics(List.of(seed.demo))
                        .status(SeriesStatus.ONGOING)
                        .coverColor(seed.coverColor)
                        .mangaka(seed.mangaka)
                        .tantouEditor(seed.tantouEditor)
                        .chapterCount(0)
                        .characters(new ArrayList<>())
                        .build();
                seriesRepository.save(series);
            }

            List<Chapter> chapters = createWeeklyChapters(series, seed.firstPublish, getChapterTitles(seed.title));
            chapterRepository.saveAll(chapters);

            series.setChapterCount(chapters.size());
            savedSeries.add(series);
        }

        seriesRepository.saveAll(savedSeries);
        System.out.println("Seeded " + savedSeries.size() + " series with "
                + savedSeries.stream().mapToInt(Series::getChapterCount).sum() + " chapters");
    }

    private List<Chapter> createWeeklyChapters(Series series, LocalDate firstDate, List<String> titles) {
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            LocalDateTime publishDate = firstDate.plusWeeks(i).atStartOfDay();
            chapters.add(Chapter.builder()
                    .series(series)
                    .chapterNumber(i + 1)
                    .title(titles.get(i))
                    .pageCount(18 + (i % 7) * 2)
                    .progressPercent(100)
                    .publishDate(publishDate)
                    .status(ChapterStatus.PUBLISHED)
                    .build());
        }
        return chapters;
    }

    private List<String> getChapterTitles(String seriesTitle) {
        return switch (seriesTitle) {
            case "Shadow Monarch" -> List.of(
                    "The Awakening", "Shadows Gather", "The Dungeon Break",
                    "The Beast Awakens", "New Powers", "The Rival Appears",
                    "The Tournament", "The Final Strike");
            case "Neon Horizon" -> List.of(
                    "Neon Dawn", "The Signal", "Underground",
                    "The Chase", "Digital Dreams", "The Network",
                    "Zero Hour", "New Genesis");
            case "Echoes of Eternity" -> List.of(
                    "The First Echo", "Timeless", "Crossroads",
                    "The Memory", "Fractured", "Reunion",
                    "The Truth", "Eternity");
            case "Blade of the Samurai" -> List.of(
                    "The Ronin's Path", "The Duel at Dawn", "Shadow of the Shogun",
                    "The Cursed Blade", "Mountains of Blood", "The Ninja's Creed",
                    "The Siege", "Honor Restored");
            case "Cherry Blossoms After Winter" -> List.of(
                    "First Spring", "Summer Rain", "Autumn Leaves",
                    "Winter Snow", "Second Spring", "The Festival",
                    "Confessions", "New Beginnings");
            case "Stardust Chronicles" -> List.of(
                    "Falling Star", "The Observatory", "Cosmic Map",
                    "The Signal", "Asteroid Belt", "The Nebula",
                    "Black Hole", "Supernova");
            case "The Last Summoner" -> List.of(
                    "The Summoning", "The Contract", "The Demon Lord",
                    "The Rebellion", "The Lost Realm", "The Cursed Sword",
                    "The Final Pact", "New World");
            default -> List.of("Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4",
                    "Chapter 5", "Chapter 6", "Chapter 7", "Chapter 8");
        };
    }

    private record SeriesSeed(
            String title, String titleJp, String synopsis,
            Genre genre, TargetDemographic demo,
            User mangaka, User tantouEditor,
            String coverColor, LocalDate firstPublish
    ) {}
}
