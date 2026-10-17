package com.example.taketoday.venue.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

/** Одна строка графика работы в форме «Сведения о заведении». */
public class WorkDayForm {

    private int dayOfWeek;
    private boolean working;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime openTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime closeTime;

    public WorkDayForm() {
    }

    public WorkDayForm(int dayOfWeek, boolean working, LocalTime openTime, LocalTime closeTime) {
        this.dayOfWeek = dayOfWeek;
        this.working = working;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    private static final String[] DAY_NAMES =
            {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};

    /** «Понедельник» … «Воскресенье» — для подписи строки. */
    public String getDayName() {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAY_NAMES[dayOfWeek - 1] : "";
    }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public boolean isWorking() { return working; }
    public void setWorking(boolean working) { this.working = working; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
}
