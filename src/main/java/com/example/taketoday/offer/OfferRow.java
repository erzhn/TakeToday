package com.example.taketoday.offer;

import com.example.taketoday.common.AppTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Строка таблицы «Управление предложениями» в кабинете заведения (экран 7). */
public final class OfferRow {

    /** Что видит сотрудник в колонке состояния. Считается из status, порций и времени. */
    public enum State {
        ACTIVE("Активно", "badge-green"),
        SOLD_OUT("Всё продано", "badge-soft-dark"),
        EXPIRED("Время вышло", "badge-soft"),
        WITHDRAWN("Снято", "badge-orange"),
        HIDDEN("Скрыто администратором", "badge-red");

        private final String title;
        private final String css;

        State(String title, String css) {
            this.title = title;
            this.css = css;
        }

        public String getTitle() { return title; }
        public String getCss() { return css; }
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));

    private final StaffOffer offer;
    private final State state;
    private final String pickupWindow;
    private final String pickupDay;

    private OfferRow(StaffOffer offer, State state, String pickupWindow, String pickupDay) {
        this.offer = offer;
        this.state = state;
        this.pickupWindow = pickupWindow;
        this.pickupDay = pickupDay;
    }

    public static OfferRow of(StaffOffer offer, Instant now) {
        return new OfferRow(offer, stateOf(offer, now), window(offer), day(offer, now));
    }

    static State stateOf(StaffOffer offer, Instant now) {
        if (offer.status() == OfferStatus.HIDDEN) {
            return State.HIDDEN;
        }
        if (offer.status() == OfferStatus.WITHDRAWN) {
            return State.WITHDRAWN;
        }
        if (offer.isExpired(now)) {
            return State.EXPIRED;
        }
        return offer.available() == 0 ? State.SOLD_OUT : State.ACTIVE;
    }

    private static String window(StaffOffer offer) {
        return offer.pickupStart().atZone(AppTime.ZONE).format(HH_MM) + " – "
                + offer.pickupEnd().atZone(AppTime.ZONE).format(HH_MM);
    }

    private static String day(StaffOffer offer, Instant now) {
        ZonedDateTime start = offer.pickupStart().atZone(AppTime.ZONE);
        LocalDate today = now.atZone(AppTime.ZONE).toLocalDate();
        LocalDate date = start.toLocalDate();
        if (date.equals(today)) return "Сегодня";
        if (date.equals(today.plusDays(1))) return "Завтра";
        if (date.equals(today.minusDays(1))) return "Вчера";
        return start.format(DAY);
    }

    public UUID getId() { return offer.id(); }
    public String getTitle() { return offer.title(); }
    public String getFoodTypeTitle() { return offer.foodType().getTitle(); }
    public String getFoodTypeIcon() { return offer.foodType().getIcon(); }
    public String getPhotoPath() { return offer.photoPath(); }
    public int getRegularPrice() { return offer.regularPrice(); }
    public int getDiscountPrice() { return offer.discountPrice(); }
    public int getDiscountPercent() { return offer.discountPercent(); }
    public int getAvailable() { return offer.available(); }
    public int getTotal() { return offer.totalPortions(); }
    public int getSold() { return offer.soldPortions(); }
    public int getReserved() { return offer.reservedPortions(); }

    /** Ширина полоски остатка в процентах. */
    public int getAvailablePercent() {
        return offer.totalPortions() == 0 ? 0 : Math.round(100f * offer.available() / offer.totalPortions());
    }

    public String getPickupWindow() { return pickupWindow; }
    public String getPickupDay() { return pickupDay; }
    public State getState() { return state; }

    /** Для фильтра «Активно»: на витрине или всё раскупили, но время ещё идёт. */
    public boolean isLive() { return state == State.ACTIVE || state == State.SOLD_OUT; }
    public boolean isOff() { return state == State.WITHDRAWN || state == State.HIDDEN; }
    public boolean isExpired() { return state == State.EXPIRED; }

    public boolean isCanWithdraw() { return offer.status() == OfferStatus.ACTIVE && state != State.EXPIRED; }
    public boolean isCanResume() { return state == State.WITHDRAWN; }
    public boolean isCanEdit() { return state != State.EXPIRED; }
    public boolean isCanRepeat() { return state == State.EXPIRED; }
}
