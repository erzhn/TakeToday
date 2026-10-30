package com.example.taketoday.pickup;

import com.example.taketoday.common.AppTime;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Заказ глазами сотрудника на кассе: что выдать, кому и когда. Код получения сюда не попадает —
 * сотрудник его уже ввёл, повторно показывать незачем.
 *
 * @param buyerName имя покупателя как есть; на экран идёт сокращённое {@link #getBuyerShortName()}
 */
public record PickupOrder(UUID id, UUID establishmentId, String title, int quantity, int amount,
                          String buyerName, String photoPath, Instant pickupStart, Instant pickupEnd,
                          String status, Instant paidAt, Instant issuedAt, String issuedByName) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"));

    public String getNumber() {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** «Айбек Д.» — сотруднику достаточно, чтобы сверить с покупателем. */
    public String getBuyerShortName() {
        String[] parts = buyerName == null ? new String[0] : buyerName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "Покупатель";
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].charAt(0) + ".";
    }

    public String getPickupWindow() {
        return pickupStart.atZone(AppTime.ZONE).format(HH_MM) + " – " + pickupEnd.atZone(AppTime.ZONE).format(HH_MM);
    }

    public String getIssuedAtText() {
        return issuedAt == null ? "" : issuedAt.atZone(AppTime.ZONE).format(DAY_TIME);
    }

    public String getPaidAtText() {
        return paidAt == null ? "" : paidAt.atZone(AppTime.ZONE).format(DAY_TIME);
    }

    /** Покупатель пришёл раньше начала окна выдачи — выдать можно, но сотрудника предупреждаем. */
    public boolean isEarly(Instant now) {
        return now.isBefore(pickupStart);
    }

    public String getStartText() {
        return pickupStart.atZone(AppTime.ZONE).format(HH_MM);
    }
}
