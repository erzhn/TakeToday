package com.example.taketoday.venue;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.venue.dto.WorkDayForm;

import java.time.LocalDate;

/** Короткая карточка заведения для кабинета сотрудника (экран 7 в Figma). */
public final class VenueSummary {

    private final String name;
    private final String categoryTitle;
    private final String address;
    private final String phone;
    private final String logoPath;
    private final String todayHours;
    private final String scheduleLabel;
    private final boolean profileComplete;

    private VenueSummary(String name, String categoryTitle, String address, String phone,
                         String logoPath, String todayHours, String scheduleLabel, boolean profileComplete) {
        this.name = name;
        this.categoryTitle = categoryTitle;
        this.address = address;
        this.phone = phone;
        this.logoPath = logoPath;
        this.todayHours = todayHours;
        this.scheduleLabel = scheduleLabel;
        this.profileComplete = profileComplete;
    }

    public static VenueSummary of(Establishment e) {
        int today = LocalDate.now(AppTime.ZONE).getDayOfWeek().getValue();
        WorkDayForm day = e.workDays().get(today - 1);
        String hours = ScheduleText.hours(day);
        String todayHours = "выходной".equals(hours) ? "Выходной" : hours;

        String scheduleLabel;
        if (!e.scheduleSaved() && e.workingHours() != null && !e.workingHours().isBlank()) {
            // График по дням ещё не заполняли — показываем то, что указали при регистрации
            scheduleLabel = "График: " + e.workingHours();
            todayHours = e.workingHours();
        } else {
            String summary = ScheduleText.of(e.workDays());
            scheduleLabel = summary.startsWith("Ежедневно")
                    ? "График: " + summary.toLowerCase()
                    : ("Выходной".equals(todayHours) ? "Сегодня выходной" : "Сегодня: " + todayHours);
        }

        return new VenueSummary(
                e.name(),
                e.category() != null ? e.category().getTitle() : null,
                e.address(),
                e.contact(),
                e.logoPath(),
                todayHours,
                scheduleLabel,
                e.isProfileComplete());
    }

    public String getName() { return name; }
    public String getCategoryTitle() { return categoryTitle; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getLogoPath() { return logoPath; }
    public String getTodayHours() { return todayHours; }
    public String getScheduleLabel() { return scheduleLabel; }
    public boolean isProfileComplete() { return profileComplete; }
}
