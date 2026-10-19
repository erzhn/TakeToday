package com.example.taketoday.venue;

import com.example.taketoday.venue.dto.WorkDayForm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Строка таблицы establishments вместе с графиком работы.
 * Таблицу создаёт V2__authentication.sql (Жаныбек), дополнительные колонки — миграция этапа 1 Эржана.
 *
 * @param contact      телефон для связи (заполняется при регистрации)
 * @param workingHours краткий текст графика, например «Пн–Пт 09:00–21:00, Сб–Вс выходной»
 * @param workDays     ровно 7 дней: 1 = понедельник … 7 = воскресенье
 * @param scheduleSaved график по дням уже сохраняли. Если нет — workDays заполнены по умолчанию,
 *                      а настоящие часы есть только текстом в workingHours (как указали при регистрации)
 */
public record Establishment(
        UUID id,
        UUID ownerId,
        String name,
        VenueCategory category,
        String description,
        String address,
        District district,
        String contact,
        String contactEmail,
        String instagram,
        String logoPath,
        String workingHours,
        Instant updatedAt,
        List<WorkDayForm> workDays,
        boolean scheduleSaved
) {
    /** Заполнены ли данные, без которых нельзя выставлять предложения (этап 2). */
    public boolean isProfileComplete() {
        return address != null && !address.isBlank();
    }
}
