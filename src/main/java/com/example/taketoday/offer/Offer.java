package com.example.taketoday.offer;

import com.example.taketoday.common.AppTime;
import java.time.Instant;
import java.util.UUID;

/** Публичные данные набора; внутренние данные владельца в каталог не передаются. */
public record Offer(UUID id, UUID establishmentId, String establishmentName, String address,
                    String title, String composition, FoodType foodType, String photoPath,
                    int regularPrice, int discountPrice, int totalPortions, int reservedPortions,
                    int soldPortions, Instant pickupStart, Instant pickupEnd, String status) {
    public int getAvailablePortions() { return totalPortions - reservedPortions - soldPortions; }
    public String getPickupStartText() { return AppTime.format(pickupStart); }
    public String getPickupEndText() { return AppTime.format(pickupEnd); }
    public String availabilityAt(Instant now) {
        if (!"ACTIVE".equals(status)) return "Предложение снято с продажи";
        if (!pickupEnd.isAfter(now)) return "Время выдачи завершилось";
        if (getAvailablePortions() == 0) return "Порции закончились";
        return "Доступно порций: " + getAvailablePortions();
    }
}
