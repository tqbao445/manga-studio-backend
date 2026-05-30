package com.mangaflow.studio.data;

import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.page.Page;
import com.mangaflow.studio.model.series.Genre;
import com.mangaflow.studio.model.series.Series;
import com.mangaflow.studio.model.series.SeriesStatus;
import com.mangaflow.studio.model.series.TargetDemographic;
import com.mangaflow.studio.repository.auth.UserRepository;
import com.mangaflow.studio.repository.page.PageRepository;
import com.mangaflow.studio.repository.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeriesRepository seriesRepository;
    private final PageRepository pageRepository;

    @Override
    public void run(String... args) {
        // ─── Users ───
        if (userRepository.count() == 0) {
            List<User> users = List.of(
                    User.builder().email("ichikawa@manga.com").username("ichikawa").displayName("Ichikawa").role(Role.MANGAKA).build(),
                    User.builder().email("tanaka@manga.com").username("tanaka").displayName("Tanaka").role(Role.ASSISTANT).build(),
                    User.builder().email("suzuki@manga.com").username("suzuki").displayName("Suzuki").role(Role.ASSISTANT).build(),
                    User.builder().email("yamamoto@manga.com").username("yamamoto").displayName("Yamamoto").role(Role.ASSISTANT).build(),
                    User.builder().email("sato@editor.com").username("sato_editor").displayName("Sato").role(Role.TANTOU_EDITOR).build(),
                    User.builder().email("taniguchi@editor.com").username("taniguchi").displayName("Taniguchi").role(Role.TANTOU_EDITOR).build(),
                    User.builder().email("kimura@board.com").username("kimura").displayName("Kimura").role(Role.EDITORIAL_BOARD).build(),
                    User.builder().email("nishida@board.com").username("nishida").displayName("Nishida").role(Role.EDITORIAL_BOARD).build(),
                    User.builder().email("fujimoto@manga.com").username("fujimoto").displayName("Fujimoto").role(Role.MANGAKA).build(),
                    User.builder().email("ito@manga.com").username("ito").displayName("Ito").role(Role.MANGAKA).build()
            );

            users.forEach(u -> u.setPassword(passwordEncoder.encode("password")));

            userRepository.saveAll(users);

            System.out.println("✅ Seeded " + users.size() + " users");
        } else {
            System.out.println("⏭️ Users already exist, skipping");
        }

        // ─── Series (cần users có sẵn — tìm bằng email) ───
        if (seriesRepository.count() == 0) {
            User ichikawa = userRepository.findByEmail("ichikawa@manga.com")
                    .orElseThrow(() -> new RuntimeException("ichikawa not found — seed users first"));
            User fujimoto = userRepository.findByEmail("fujimoto@manga.com")
                    .orElseThrow(() -> new RuntimeException("fujimoto not found — seed users first"));
            User ito = userRepository.findByEmail("ito@manga.com")
                    .orElseThrow(() -> new RuntimeException("ito not found — seed users first"));
            User sato = userRepository.findByEmail("sato@editor.com")
                    .orElseThrow(() -> new RuntimeException("sato not found — seed users first"));
            User taniguchi = userRepository.findByEmail("taniguchi@editor.com")
                    .orElseThrow(() -> new RuntimeException("taniguchi not found — seed users first"));

            List<Series> seriesList = List.of(
                    Series.builder()
                            .title("Dragon's Awakening")
                            .titleJp("ドラゴンの覚醒")
                            .synopsis("A young boy discovers he is the reincarnation of the dragon king.")
                            .genre(Genre.ACTION)
                            .targetDemographic(TargetDemographic.SHONEN)
                            .status(SeriesStatus.DRAFT)
                            .coverColor("#e63946")
                            .isMature(false)
                            .mangaka(ichikawa)
                            .build(),

                    Series.builder()
                            .title("Love in Tokyo")
                            .titleJp("東京の恋")
                            .synopsis("Two strangers meet in a rainy Shibuya crossing.")
                            .genre(Genre.ROMANCE)
                            .targetDemographic(TargetDemographic.SHONEN)
                            .status(SeriesStatus.PENDING_APPROVAL)
                            .coverColor("#ffb703")
                            .isMature(false)
                            .mangaka(fujimoto)
                            .build(),

                    Series.builder()
                            .title("Phantom Thief K")
                            .titleJp("怪盗K")
                            .synopsis("A mysterious thief steals from the corrupt rich.")
                            .genre(Genre.ACTION)
                            .targetDemographic(TargetDemographic.SHONEN)
                            .status(SeriesStatus.ONGOING)
                            .coverColor("#457b9d")
                            .isMature(false)
                            .mangaka(ichikawa)
                            .tantouEditor(sato)
                            .chapterCount(24)
                            .currentRank(128)
                            .currentTier("SILVER")
                            .build(),

                    Series.builder()
                            .title("Silent Rain")
                            .titleJp("静かな雨")
                            .synopsis("A psychological thriller set in a secluded mountain village.")
                            .genre(Genre.DRAMA)
                            .targetDemographic(TargetDemographic.SEINEN)
                            .status(SeriesStatus.HIATUS)
                            .coverColor("#1d3557")
                            .isMature(true)
                            .mangaka(fujimoto)
                            .tantouEditor(taniguchi)
                            .chapterCount(8)
                            .currentRank(512)
                            .currentTier("BRONZE")
                            .build(),

                    Series.builder()
                            .title("Starward Journey")
                            .titleJp("星への旅")
                            .synopsis("An epic space opera spanning a thousand years.")
                            .genre(Genre.FANTASY)
                            .targetDemographic(TargetDemographic.SHONEN)
                            .status(SeriesStatus.COMPLETED)
                            .coverColor("#2a9d8f")
                            .isMature(false)
                            .mangaka(ito)
                            .tantouEditor(sato)
                            .chapterCount(64)
                            .currentRank(15)
                            .currentTier("GOLD")
                            .build()
            );

            seriesRepository.saveAll(seriesList);

            System.out.println("✅ Seeded " + seriesList.size() + " series");
        } else {
            System.out.println("⏭️ Series already exist, skipping");
        }

        // ─── Pages (cần series có sẵn) ───
        if (pageRepository.count() == 0) {
            // Phantom Thief K (ichikawa) — chapter 1: 3 pages
            List<Page> pages = List.of(
                    Page.builder()
                            .chapterId(1L).pageNumber(1)
                            .originalImageUrl("https://placehold.co/1200x1800?text=Ch1+P1")
                            .webImageUrl("https://placehold.co/800x1200?text=Ch1+P1")
                            .thumbnailUrl("https://placehold.co/200x300?text=P1")
                            .publicId("seed/ch1/p1").width(1200).height(1800)
                            .build(),
                    Page.builder()
                            .chapterId(1L).pageNumber(2)
                            .originalImageUrl("https://placehold.co/1200x1800?text=Ch1+P2")
                            .webImageUrl("https://placehold.co/800x1200?text=Ch1+P2")
                            .thumbnailUrl("https://placehold.co/200x300?text=P2")
                            .publicId("seed/ch1/p2").width(1200).height(1800)
                            .build(),
                    Page.builder()
                            .chapterId(1L).pageNumber(3)
                            .originalImageUrl("https://placehold.co/1200x1800?text=Ch1+P3")
                            .webImageUrl("https://placehold.co/800x1200?text=Ch1+P3")
                            .thumbnailUrl("https://placehold.co/200x300?text=P3")
                            .publicId("seed/ch1/p3").width(1200).height(1800)
                            .build(),
                    // Starward Journey (ito) — chapter 1: 2 pages
                    Page.builder()
                            .chapterId(2L).pageNumber(1)
                            .originalImageUrl("https://placehold.co/1200x1800?text=Star+P1")
                            .webImageUrl("https://placehold.co/800x1200?text=Star+P1")
                            .thumbnailUrl("https://placehold.co/200x300?text=Star+P1")
                            .publicId("seed/ch2/p1").width(1200).height(1800)
                            .build(),
                    Page.builder()
                            .chapterId(2L).pageNumber(2)
                            .originalImageUrl("https://placehold.co/1200x1800?text=Star+P2")
                            .webImageUrl("https://placehold.co/800x1200?text=Star+P2")
                            .thumbnailUrl("https://placehold.co/200x300?text=Star+P2")
                            .publicId("seed/ch2/p2").width(1200).height(1800)
                            .build()
            );

            pageRepository.saveAll(pages);

            System.out.println("✅ Seeded " + pages.size() + " pages");
        } else {
            System.out.println("⏭️ Pages already exist, skipping");
        }
    }
}
