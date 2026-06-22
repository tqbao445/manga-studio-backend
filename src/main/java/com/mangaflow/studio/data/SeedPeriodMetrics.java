package com.mangaflow.studio.data;

import com.mangaflow.studio.model.metric.SeriesPeriodMetric;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.repository.metric.SeriesPeriodMetricRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(3)
@RequiredArgsConstructor
public class SeedPeriodMetrics implements CommandLineRunner {

    private final SeriesRepository seriesRepository;
    private final SeriesPeriodMetricRepository metricRepository;

    @Override
    public void run(String... args) {
        if (metricRepository.findByPeriodLabelAndPeriodTypeOrderByScoreDesc("2026-W22", "WEEKLY").size() > 0) {
            System.out.println("Period metrics already exist, skipping");
            return;
        }

        Map<String, Series> seriesMap = loadSeries();

        seedWeekly("2026-W22", seriesMap, List.of(
                entry("Shadow Monarch", 450, 8.8, 1),
                entry("Cherry Blossoms After Winter", 280, 9.5, 2),
                entry("Echoes of Eternity", 310, 9.0, 3),
                entry("Blade of the Samurai", 350, 8.2, 4),
                entry("Neon Horizon", 380, 7.5, 5),
                entry("Stardust Chronicles", 220, 7.0, 6)
        ));

        seedWeekly("2026-W23", seriesMap, List.of(
                entry("Shadow Monarch", 480, 9.0, 1),
                entry("Neon Horizon", 420, 8.5, 2),
                entry("Cherry Blossoms After Winter", 300, 9.3, 3),
                entry("Echoes of Eternity", 280, 9.1, 4),
                entry("Blade of the Samurai", 320, 8.5, 5),
                entry("Stardust Chronicles", 250, 7.5, 6)
        ));

        seedWeekly("2026-W24", seriesMap, List.of(
                entry("Shadow Monarch", 510, 9.2, 1),
                entry("Echoes of Eternity", 350, 9.3, 2),
                entry("Neon Horizon", 450, 8.2, 3),
                entry("Cherry Blossoms After Winter", 320, 9.0, 4),
                entry("Blade of the Samurai", 300, 8.8, 5),
                entry("Stardust Chronicles", 280, 7.8, 6)
        ));

        seedMonthly("2026-06", seriesMap, List.of(
                entry("The Last Summoner", 1200, 8.5, 1)
        ));

        updateCurrentRanks(seriesMap);

        System.out.println("Seeded period metrics for W22, W23, W24 and 2026-06");
    }

    private Map<String, Series> loadSeries() {
        List<Series> all = seriesRepository.findAll();
        return Map.of(
                "Shadow Monarch", findByTitle(all, "Shadow Monarch"),
                "Neon Horizon", findByTitle(all, "Neon Horizon"),
                "Echoes of Eternity", findByTitle(all, "Echoes of Eternity"),
                "Blade of the Samurai", findByTitle(all, "Blade of the Samurai"),
                "Cherry Blossoms After Winter", findByTitle(all, "Cherry Blossoms After Winter"),
                "Stardust Chronicles", findByTitle(all, "Stardust Chronicles"),
                "The Last Summoner", findByTitle(all, "The Last Summoner")
        );
    }

    private Series findByTitle(List<Series> list, String title) {
        return list.stream().filter(s -> title.equals(s.getTitle())).findFirst().orElseThrow();
    }

    private SeriesEntry entry(String title, long votes, double avgScore, int rank) {
        return new SeriesEntry(title, votes, avgScore, rank);
    }

    private void seedWeekly(String periodLabel, Map<String, Series> seriesMap, List<SeriesEntry> entries) {
        for (SeriesEntry e : entries) {
            double score = e.votes * 0.7 + e.avgScore * 100;
            metricRepository.save(SeriesPeriodMetric.builder()
                    .series(seriesMap.get(e.title))
                    .periodLabel(periodLabel)
                    .periodType("WEEKLY")
                    .totalVotes(e.votes)
                    .avgScore(e.avgScore)
                    .score(score)
                    .rank(e.rank)
                    .build());
        }
    }

    private void seedMonthly(String periodLabel, Map<String, Series> seriesMap, List<SeriesEntry> entries) {
        for (SeriesEntry e : entries) {
            double score = e.votes * 0.7 + e.avgScore * 100;
            metricRepository.save(SeriesPeriodMetric.builder()
                    .series(seriesMap.get(e.title))
                    .periodLabel(periodLabel)
                    .periodType("MONTHLY")
                    .totalVotes(e.votes)
                    .avgScore(e.avgScore)
                    .score(score)
                    .rank(e.rank)
                    .build());
        }
    }

    private void updateCurrentRanks(Map<String, Series> seriesMap) {
        seriesMap.get("Shadow Monarch").setCurrentRank(1);
        seriesMap.get("Echoes of Eternity").setCurrentRank(2);
        seriesMap.get("Neon Horizon").setCurrentRank(3);
        seriesMap.get("Cherry Blossoms After Winter").setCurrentRank(4);
        seriesMap.get("Blade of the Samurai").setCurrentRank(5);
        seriesMap.get("Stardust Chronicles").setCurrentRank(6);
        seriesMap.get("The Last Summoner").setCurrentRank(1);
        seriesRepository.saveAll(seriesMap.values());
    }

    private record SeriesEntry(String title, long votes, double avgScore, int rank) {}
}
