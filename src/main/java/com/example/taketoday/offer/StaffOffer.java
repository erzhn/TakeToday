package com.example.taketoday.offer;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка таблицы offers (V5__offers.sql, Жаныбек) со всеми полями — для кабинета заведения.
 * Для каталога используется публичная проекция {@link Offer} (без служебных полей, с названием заведения).
 * Цены — целые сомы.
 *
 * @param reservedPortions порции в брони: ждут оплаты (этап 3)
 * @param soldPortions     оплаченные порции
 * @param version          растёт при каждом изменении — защита от одновременного редактирования
 */
public record StaffOffer(
        UUID id,
        UUID establishmentId,
        String title,
        String composition,
        FoodType foodType,
        String photoPath,
        int regularPrice,
        int discountPrice,
        int totalPortions,
        int reservedPortions,
        int soldPortions,
        Instant pickupStart,
        Instant pickupEnd,
        OfferStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    /** Сколько порций ещё можно забронировать. */
    public int available() {
        return Math.max(0, totalPortions - reservedPortions - soldPortions);
    }

    /** Меньше этого числа порций сделать нельзя (п. 4.4.2 ТЗ). */
    public int lockedPortions() {
        return reservedPortions + soldPortions;
    }

    /** Время выдачи уже закончилось. */
    public boolean isExpired(Instant now) {
        return !pickupEnd.isAfter(now);
    }

    /** Можно ли сейчас забронировать порцию (п. 4.3.4 ТЗ). */
    public boolean isBookable(Instant now) {
        return status == OfferStatus.ACTIVE && available() > 0 && !isExpired(now);
    }

    /** Скидка в процентах, округлённая: 500 → 250 даёт 50. */
    public int discountPercent() {
        return Math.round(100f * (regularPrice - discountPrice) / regularPrice);
    }
}
