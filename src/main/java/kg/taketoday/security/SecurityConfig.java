package kg.taketoday.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Доступ к разделам по роли (п. 4.1.2, 5.2.3 ТЗ).
 *
 * Разделы:
 *   /, /offers/**          — всем (каталог)
 *   /profile/**, /orders/** — любому вошедшему пользователю
 *   /staff/**              — сотруднику заведения
 *   /admin/**              — администратору
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/offers/**", "/css/**", "/js/**", "/images/**",
                        "/login", "/register", "/forgot-password", "/error").permitAll()
                .requestMatchers("/staff/**").hasRole("STAFF")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // Пока используется стандартная страница входа Spring.
            // Свою страницу /login сделает Жаныбек (этап 1) — тогда добавить .loginPage("/login").
            .formLogin(Customizer.withDefaults())
            .logout(logout -> logout.logoutSuccessUrl("/"));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
