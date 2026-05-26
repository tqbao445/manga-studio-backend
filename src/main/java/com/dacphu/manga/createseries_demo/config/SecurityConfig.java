package com.dacphu.manga.createseries_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // tắt csrf để test postman
                .csrf(csrf -> csrf.disable())

                // cho phép gọi API
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**")
                        .permitAll()
                        .anyRequest()
                        .permitAll()
                )

                // tắt login mặc định
                .formLogin(form -> form.disable())

                // tắt basic auth
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }
}
