package com.mangaflow.studio.data;

import com.mangaflow.studio.auth.model.Role;
import com.mangaflow.studio.auth.model.User;
import com.mangaflow.studio.auth.repository.UserRepository;
import com.mangaflow.studio.series.enums.Genre;
import com.mangaflow.studio.series.enums.SeriesStatus;
import com.mangaflow.studio.series.enums.TargetDemographic;
import com.mangaflow.studio.series.model.Series;
import com.mangaflow.studio.series.repository.SeriesRepository;
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
    }
}
