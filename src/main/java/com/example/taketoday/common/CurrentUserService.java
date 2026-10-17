package com.example.taketoday.common;

import com.example.taketoday.auth.AuthModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Текущий вошедший пользователь.
 *
 * После входа по коду (AuthController, Жаныбек) в сессии лежит AuthModels.User.
 * Из него берём только id, а всё остальное (имя, телефон, заведение) читаем из базы —
 * так шапка и кабинет сразу показывают изменения из профиля.
 */
@Service
public class CurrentUserService {

    private final JdbcTemplate db;

    public CurrentUserService(JdbcTemplate db) {
        this.db = db;
    }

    /** Пользователь из сессии (как его положил AuthController) или пусто для гостя. */
    public Optional<AuthModels.User> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthModels.User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Свежие данные вошедшего пользователя или пусто для гостя. */
    public Optional<CurrentAccount> find() {
        return principal().flatMap(user -> load(user.id()));
    }

    /** То же, но для страниц, куда гость попасть не может (их закрывает SecurityConfig). */
    public CurrentAccount require() {
        return find().orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
    }

    private Optional<CurrentAccount> load(UUID id) {
        return db.query("""
                        SELECT a.id, a.email, a.name, a.phone, a.role, e.id AS establishment_id, e.name AS establishment_name
                        FROM accounts a
                        LEFT JOIN establishments e ON e.owner_id = a.id
                        WHERE a.id = ?
                        """,
                (rs, n) -> new CurrentAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("role"),
                        rs.getObject("establishment_id", UUID.class),
                        rs.getString("establishment_name")),
                id).stream().findFirst();
    }
}
