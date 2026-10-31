package com.example.taketoday.payment;

import com.example.taketoday.common.AppTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Заказ (reservation_orders, Жаныбек) с полями оплаты и кода (V8, Эржан) — для оплаты и страницы заказа.
 *
 * @param regularPrice обычная цена набора за порцию — чтобы показать экономию
 * @param photoPath    фото набора из offers
 */
public record PaymentOrder(UUID id, UUID customerId, UUID offerId, String title, String establishmentName,
                           String address, int amount, int quantity, int unitPrice, int regularPrice, String photoPath,
                           Instant pickupStart, Instant pickupEnd, String status, Instant expiresAt,
                           String pickupCode, Instant paidAt, String cancelReason, String refundStatus,
                           Instant issuedAt, Instant cancelledAt, String cancelComment) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));

    public boolean isPaid() { return "PAID".equals(status); }
    /** Этап 4: сотрудник подтвердил выдачу, код больше не действует. */
    public boolean isIssued() { return "ISSUED".equals(status); }
    public boolean isAwaitingPayment() { return "AWAITING_PAYMENT".equals(status); }
    public boolean isCancelled() { return "CANCELLED".equals(status); }
    public boolean isLatePayment() { return "LATE_PAYMENT".equals(cancelReason); }

    /** Этап 5: отменил покупатель, заведение или администратор (не «бронь истекла»). */
    public boolean isCancelledByPerson() {
        return isCancelled() && ("CUSTOMER".equals(cancelReason) || "VENUE".equals(cancelReason) || "ADMIN".equals(cancelReason));
    }

    /** Заказ показывается на странице /orders/{id}; остальное (бронь, истёкшая бронь) — на странице брони. */
    public boolean hasOrderPage() {
        return isPaid() || isIssued() || isLatePayment() || isCancelledByPerson() || "PICKUP_EXPIRED".equals(status);
    }

    /** Отменить оплаченный заказ покупатель может до начала выдачи (п. 4.6.4). */
    public boolean canCancel(Instant now) {
        return isPaid() && now.isBefore(pickupStart);
    }

    /** Окно выдачи прошло, а заказ так и не забрали. */
    public boolean isPickupOver(Instant now) {
        return "PICKUP_EXPIRED".equals(status) || (isPaid() && !now.isBefore(pickupEnd));
    }

    public String getCancelledAtText() {
        return cancelledAt == null ? "" : AppTime.format(cancelledAt);
    }

    public String getPickupStartText() {
        return pickupStart.atZone(AppTime.ZONE).format(HH_MM);
    }

    public String getRefundTitle() {
        return switch (refundStatus == null ? "NONE" : refundStatus) {
            case "PENDING" -> "Возврат в обработке";
            case "COMPLETED" -> "Деньги возвращены";
            case "FAILED" -> "Возврат задерживается — им занимается администратор";
            default -> "";
        };
    }

    public String getCancelTitle() {
        return switch (cancelReason == null ? "" : cancelReason) {
            case "CUSTOMER" -> paidAt == null ? "Вы отменили бронь" : "Вы отменили заказ";
            case "VENUE" -> "Заведение не может выдать заказ";
            case "ADMIN" -> "Заказ отменён администратором";
            default -> "Заказ отменён";
        };
    }

    /** Бронь ещё действует и оплату можно начать. */
    public boolean canPay(Instant now) {
        return isAwaitingPayment() && expiresAt.isAfter(now);
    }

    /** Короткий номер заказа для людей: первые 8 символов UUID, «№ 3F2A91C0». */
    public String getNumber() {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** «482 913» — две группы, так проще продиктовать кассиру. */
    public String getCodeGroups() {
        return pickupCode == null ? "" : pickupCode.substring(0, 3) + " " + pickupCode.substring(3);
    }

    public int getSaving() {
        return Math.max(0, regularPrice * quantity - amount);
    }

    public String getPickupWindow() {
        return pickupStart.atZone(AppTime.ZONE).format(HH_MM) + " – " + pickupEnd.atZone(AppTime.ZONE).format(HH_MM);
    }

    /** «Сегодня», «Завтра» или «24 октября». */
    public String getPickupDay() {
        LocalDate date = pickupStart.atZone(AppTime.ZONE).toLocalDate();
        LocalDate today = LocalDate.now(AppTime.ZONE);
        if (date.equals(today)) return "Сегодня";
        if (date.equals(today.plusDays(1))) return "Завтра";
        return date.format(DAY);
    }

    public String getIssuedAtText() {
        return issuedAt == null ? "" : AppTime.format(issuedAt);
    }

    public String getPaidAtText() {
        return paidAt == null ? "" : AppTime.format(paidAt);
    }
}
