package com.example.taketoday.venue;

import com.example.taketoday.venue.dto.WorkDayForm;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Превращает график по дням в короткий текст для establishments.working_hours:
 * «Ежедневно 09:00–21:00» или «Пн–Пт 09:00–21:00, Сб 10:00–20:00, Вс выходной».
 * Соседние дни с одинаковыми часами объединяются.
 */
public final class ScheduleText {

    private static final String[] SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleText() {
    }

    public static String of(List<WorkDayForm> days) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < days.size()) {
            int end = start;
            while (end + 1 < days.size() && sameHours(days.get(start), days.get(end + 1))) {
                end++;
            }
            parts.add(range(start, end) + " " + hours(days.get(start)));
            start = end + 1;
        }
        if (parts.size() == 1 && days.size() == 7 && days.get(0).isWorking()) {
            return "Ежедневно " + hours(days.get(0));
        }
        return String.join(", ", parts);
    }

    /** Часы одного дня: «09:00–21:00» или «выходной». */
    public static String hours(WorkDayForm day) {
        if (!day.isWorking() || day.getOpenTime() == null || day.getCloseTime() == null) {
            return "выходной";
        }
        return day.getOpenTime().format(HH_MM) + "–" + day.getCloseTime().format(HH_MM);
    }

    private static boolean sameHours(WorkDayForm a, WorkDayForm b) {
        if (!a.isWorking() && !b.isWorking()) {
            return true;
        }
        return a.isWorking() == b.isWorking()
                && Objects.equals(a.getOpenTime(), b.getOpenTime())
                && Objects.equals(a.getCloseTime(), b.getCloseTime());
    }

    private static String range(int start, int end) {
        return start == end ? SHORT[start] : SHORT[start] + "–" + SHORT[end];
    }
}
