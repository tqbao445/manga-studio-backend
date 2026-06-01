package com.mangaflow.studio.data;

import com.mangaflow.studio.model.auth.Role;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.repository.auth.UserRepository;
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

        System.out.println("✅ DataSeeder completed — users only");
    }
}
