package com.example.taketoday.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Время сайта. В базе всё хранится в UTC, а показываем по Бишкеку.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Bishkek");
    private static final Locale RU = Locale.forLanguageTag("ru");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", RU);

    private AppTime() {
    }

    /** «17 октября 2026» — сегодняшняя дата в Бишкеке. */
    public static String today() {
        return LocalDate.now(ZONE).format(DATE);
    }

    /** «17 октября 2026, 16:45» или «ещё не сохранялись», если времени нет. */
    public static String format(Instant instant) {
        return instant == null ? "ещё не сохранялись" : instant.atZone(ZONE).format(DATE_TIME);
    }
}
