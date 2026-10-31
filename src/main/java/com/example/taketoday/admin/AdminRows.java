package com.example.taketoday.admin;

import com.example.taketoday.common.AppTime;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Строки таблиц админ-панели (п. 4.10 ТЗ). Только то, что нужно показать, — без паролей и кодов получения. */
public final class AdminRows {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private AdminRows() {
    }

    static String shortTime(Instant instant) {
        return instant == null ? "—" : instant.atZone(AppTime.ZONE).format(SHORT);
    }

    static String number(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public record Overview(long users, long buyers, long staff, long blocked, long venues, long activeOffers,
                           long ordersToday, long paidToday, long revenueToday, long issuedToday,
                           long refundsPending, long refundsFailed, long refundedToday) {
    }

    public record User(UUID id, String name, String email, String phone, String role, boolean verified,
                       boolean blocked, String blockedReason, Instant blockedAt, Instant createdAt,
                       String establishmentName, long orders) {
        public String getRoleTitle() {
            return switch (role) {
                case "CUSTOMER" -> "Покупатель";
                case "STAFF" -> "Сотрудник заведения";
                default -> "Администратор";
            };
        }
        public String getCreatedText() { return shortTime(createdAt); }
        public String getBlockedText() { return shortTime(blockedAt); }
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }

    public record Venue(UUID id, String name, String address, String category, String contact, String ownerName,
                        String ownerEmail, UUID ownerId, boolean ownerBlocked, long activeOffers, long totalOffers,
                        long paidOrders, long revenue) {
    }

    public record Offer(UUID id, String title, String venueName, int regularPrice, int discountPrice, int total,
                        int reserved, int sold, Instant pickupStart, Instant pickupEnd, String status,
                        String hiddenReason, boolean ownerBlocked, String photoPath) {
        public int getAvailable() { return Math.max(0, total - reserved - sold); }
        public String getPickupText() {
            return shortTime(pickupStart) + " – " + pickupEnd.atZone(AppTime.ZONE).format(HH_MM);
        }
        public boolean isHidden() { return "HIDDEN".equals(status); }
        public boolean isExpired() { return pickupEnd.isBefore(Instant.now()); }
        public String getStatusTitle() {
            if (isHidden()) return "Скрыто администратором";
            if ("WITHDRAWN".equals(status)) return "Снято заведением";
            if (isExpired()) return "Время вышло";
            return getAvailable() == 0 ? "Порции закончились" : "В продаже";
        }
        public String getStatusCss() {
            if (isHidden()) return "badge-red";
            if ("WITHDRAWN".equals(status) || isExpired()) return "badge-soft";
            return getAvailable() == 0 ? "badge-soft-dark" : "badge-green";
        }
    }

    /** Платёж вместе с заказом, покупателем и заведением (п. 4.10.2). */
    public record Payment(UUID id, UUID orderId, Instant createdAt, int amount, String status, String cardLast4,
                          String errorMessage, String orderStatus, String refundStatus, String buyerName,
                          String buyerEmail, String venueName, String title) {
        public String getNumber() { return number(orderId); }
        public String getCreatedText() { return shortTime(createdAt); }
        public String getStatusTitle() {
            return switch (status) {
                case "SUCCEEDED" -> "Успешно";
                case "FAILED" -> "Ошибка оплаты";
                case "CREATED" -> "Ожидание оплаты";
                default -> "Заменён новой попыткой";
            };
        }
        public String getStatusCss() {
            return switch (status) {
                case "SUCCEEDED" -> "badge-green";
                case "FAILED" -> "badge-red";
                case "CREATED" -> "status-pending";
                default -> "badge-soft";
            };
        }
        public String getOrderStatusTitle() { return orderStatusTitle(orderStatus); }
        public String getRefundTitle() { return refundTitle(refundStatus); }
        public String getRefundCss() { return refundCss(refundStatus); }
    }

    public record Refund(UUID id, UUID orderId, int amount, String status, String initiatedBy, String adminName,
                         String reason, String errorMessage, int attempts, Instant createdAt, Instant completedAt,
                         String buyerName, String title) {
        public String getNumber() { return number(orderId); }
        public String getCreatedText() { return shortTime(createdAt); }
        public String getCompletedText() { return shortTime(completedAt); }
        public String getStatusTitle() { return refundTitle(status); }
        public String getStatusCss() { return refundCss(status); }
        public String getInitiatedTitle() { return "ADMIN".equals(initiatedBy) ? "Вручную" : "Автоматически"; }
    }

    public record Event(Instant createdAt, String eventType, UUID orderId, String userName, String details) {
        public String getCreatedText() { return shortTime(createdAt); }
        public String getNumber() { return number(orderId); }
        public String getCss() {
            return eventType.contains("FAIL") || eventType.contains("ERROR") || eventType.contains("WRONG")
                    || eventType.contains("UNAVAILABLE") || eventType.contains("LATE") ? "badge-red"
                    : eventType.contains("DUPLICATE") || eventType.contains("REFUSED") || eventType.contains("BLOCK")
                    || eventType.contains("HIDDEN") || eventType.contains("CANCEL") ? "status-pending"
                    : "badge-soft";
        }
    }

    /** Заказ целиком для страницы администратора. */
    public record Order(UUID id, String title, String venueName, String address, int amount, int quantity,
                        String status, String cancelReason, String cancelComment, String refundStatus,
                        String buyerName, String buyerEmail, Instant createdAt, Instant paidAt, Instant issuedAt,
                        Instant cancelledAt, Instant pickupStart, Instant pickupEnd) {
        public String getNumber() { return number(id); }
        public String getStatusTitle() { return orderStatusTitle(status); }
        public String getRefundTitle() { return refundTitle(refundStatus); }
        public String getRefundCss() { return refundCss(refundStatus); }
        public String getCreatedText() { return shortTime(createdAt); }
        public String getPaidText() { return shortTime(paidAt); }
        public String getIssuedText() { return shortTime(issuedAt); }
        public String getCancelledText() { return shortTime(cancelledAt); }
        public String getPickupText() {
            return shortTime(pickupStart) + " – " + pickupEnd.atZone(AppTime.ZONE).format(HH_MM);
        }
        public boolean isPaid() { return "PAID".equals(status); }
        public String getCancelReasonTitle() {
            if (cancelReason == null) return "";
            return switch (cancelReason) {
                case "CUSTOMER" -> "отменил покупатель";
                case "VENUE" -> "заведение не может выдать";
                case "ADMIN" -> "отменил администратор";
                case "LATE_PAYMENT" -> "оплата пришла после окончания брони";
                case "RESERVATION_EXPIRED" -> "бронь не оплачена за 3 минуты";
                default -> cancelReason;
            };
        }
    }

    static String orderStatusTitle(String status) {
        return switch (status) {
            case "AWAITING_PAYMENT" -> "Ожидает оплаты";
            case "PAID" -> "Оплачен";
            case "ISSUED" -> "Выдан";
            case "PICKUP_EXPIRED" -> "Время получения истекло";
            default -> "Отменён";
        };
    }

    static String refundTitle(String status) {
        return switch (status == null ? "NONE" : status) {
            case "PENDING" -> "Возврат в обработке";
            case "COMPLETED" -> "Деньги возвращены";
            case "FAILED" -> "Ошибка возврата";
            default -> "—";
        };
    }

    static String refundCss(String status) {
        return switch (status == null ? "NONE" : status) {
            case "PENDING" -> "status-pending";
            case "COMPLETED" -> "badge-green";
            case "FAILED" -> "badge-red";
            default -> "";
        };
    }
}
