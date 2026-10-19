package com.example.taketoday.venue;

import com.example.taketoday.venue.dto.WorkDayForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доступ к таблицам establishments и establishment_work_days через JdbcTemplate —
 * так же, как работает AuthService Жаныбека.
 */
@Repository
public class EstablishmentRepository {

    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(21, 0);

    private final JdbcTemplate db;

    public EstablishmentRepository(JdbcTemplate db) {
        this.db = db;
    }

    /** Заведение сотрудника. В establishments у каждого владельца ровно одно заведение (owner_id UNIQUE). */
    public Optional<Establishment> findByOwner(UUID ownerId) {
        return db.query("SELECT * FROM establishments WHERE owner_id = ?", (rs, n) -> map(rs), ownerId)
                .stream().findFirst();
    }

    /** Сохраняет сведения и график. Обновляет только строку этого заведения. */
    public void update(UUID id, Establishment e) {
        db.update("""
                        UPDATE establishments
                        SET name = ?, category = ?, description = ?, address = ?, district = ?, contact = ?,
                            contact_email = ?, instagram = ?, logo_path = ?, working_hours = ?, updated_at = ?
                        WHERE id = ?
                        """,
                e.name(),
                e.category() == null ? null : e.category().name(),
                e.description(),
                e.address(),
                e.district() == null ? null : e.district().name(),
                e.contact(),
                e.contactEmail(),
                e.instagram(),
                e.logoPath(),
                e.workingHours(),
                Timestamp.from(Instant.now()),
                id);

        db.update("DELETE FROM establishment_work_days WHERE establishment_id = ?", id);
        for (WorkDayForm day : e.workDays()) {
            db.update("""
                            INSERT INTO establishment_work_days (establishment_id, day_of_week, working, open_time, close_time)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    id, day.getDayOfWeek(), day.isWorking(),
                    day.isWorking() ? day.getOpenTime() : null,
                    day.isWorking() ? day.getCloseTime() : null);
        }
    }

    private Establishment map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Timestamp updated = rs.getTimestamp("updated_at");
        List<WorkDayForm> saved = savedWorkDays(id);
        return new Establishment(
                id,
                rs.getObject("owner_id", UUID.class),
                rs.getString("name"),
                parse(VenueCategory.class, rs.getString("category")),
                rs.getString("description"),
                rs.getString("address"),
                parse(District.class, rs.getString("district")),
                rs.getString("contact"),
                rs.getString("contact_email"),
                rs.getString("instagram"),
                rs.getString("logo_path"),
                rs.getString("working_hours"),
                updated == null ? null : updated.toInstant(),
                withDefaults(saved),
                !saved.isEmpty());
    }

    private List<WorkDayForm> savedWorkDays(UUID establishmentId) {
        return db.query(
                "SELECT * FROM establishment_work_days WHERE establishment_id = ? ORDER BY day_of_week",
                (rs, n) -> new WorkDayForm(
                        rs.getInt("day_of_week"),
                        rs.getBoolean("working"),
                        rs.getObject("open_time", LocalTime.class),
                        rs.getObject("close_time", LocalTime.class)),
                establishmentId);
    }

    /** 7 дней графика. Дни, которых ещё нет в базе, — рабочие 09:00–21:00. */
    private static List<WorkDayForm> withDefaults(List<WorkDayForm> saved) {
        List<WorkDayForm> result = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            result.add(saved.stream().filter(w -> w.getDayOfWeek() == d).findFirst()
                    .orElseGet(() -> new WorkDayForm(d, true, DEFAULT_OPEN, DEFAULT_CLOSE)));
        }
        return result;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
