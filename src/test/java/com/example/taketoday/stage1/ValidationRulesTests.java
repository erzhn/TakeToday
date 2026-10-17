package com.example.taketoday.stage1;

import com.example.taketoday.common.ValidationRules;
import com.example.taketoday.venue.ScheduleText;
import com.example.taketoday.venue.dto.WorkDayForm;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTests {

    @Test
    void phoneIsNormalized() {
        assertThat(ValidationRules.normalizePhone("+996700123456")).isEqualTo("+996 700 123 456");
        assertThat(ValidationRules.normalizePhone("996 (700) 12-34-56")).isEqualTo("+996 700 123 456");
        assertThat(ValidationRules.normalizePhone("  ")).isNull();
        assertThat(ValidationRules.normalizePhone(null)).isNull();
    }

    @Test
    void phoneRegexAcceptsKyrgyzNumbers() {
        assertThat("+996 700 123 456").matches(ValidationRules.PHONE_REGEX);
        assertThat("+996700123456").matches(ValidationRules.PHONE_REGEX);
        assertThat("").matches(ValidationRules.PHONE_REGEX);
        assertThat("12345").doesNotMatch(ValidationRules.PHONE_REGEX);
        assertThat("+7 700 123 45 67").doesNotMatch(ValidationRules.PHONE_REGEX);
    }

    @Test
    void passwordBytesLimit() {
        assertThat(ValidationRules.fitsPasswordBytes("a".repeat(72))).isTrue();
        assertThat(ValidationRules.fitsPasswordBytes("a".repeat(73))).isFalse();
        // кириллица занимает 2 байта: 40 букв = 80 байт
        assertThat(ValidationRules.fitsPasswordBytes("пароль".repeat(7))).isFalse();
    }

    @Test
    void scheduleSummary() {
        List<WorkDayForm> days = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            days.add(new WorkDayForm(d, true, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        }
        assertThat(ScheduleText.of(days)).isEqualTo("Ежедневно 09:00–21:00");

        days.set(5, new WorkDayForm(6, true, LocalTime.of(10, 0), LocalTime.of(20, 0)));
        days.set(6, new WorkDayForm(7, false, null, null));
        assertThat(ScheduleText.of(days)).isEqualTo("Пн–Пт 09:00–21:00, Сб 10:00–20:00, Вс выходной");
    }
}
