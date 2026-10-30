package com.example.taketoday.orderhistory;

import com.example.taketoday.common.AppTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class OrderHistoryEntry {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UUID id;
    private final String title;
    private final String establishmentName;
    private final String address;
    private final int amount;
    private final int quantity;
    private final Instant createdAt;
    private final Instant pickupStart;
    private final Instant pickupEnd;
    private final String pickupCode;
    private final String status;
    private final String cancelReason;
    private final String refundStatus;
    private final String customerName;
    private final String customerEmail;

    public OrderHistoryEntry(UUID id, String title, String establishmentName, String address, int amount,
                             int quantity, Instant createdAt, Instant pickupStart, Instant pickupEnd, String pickupCode,
                             String status, String cancelReason, String refundStatus,
                             String customerName, String customerEmail) {
        this.id = id;
        this.title = title;
        this.establishmentName = establishmentName;
        this.address = address;
        this.amount = amount;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.pickupStart = pickupStart;
        this.pickupEnd = pickupEnd;
        this.pickupCode = pickupCode;
        this.status = status;
        this.cancelReason = cancelReason;
        this.refundStatus = refundStatus;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
    }

    public UUID getId() { return id; }
    public String getNumber() { return id.toString().substring(0, 8).toUpperCase(Locale.ROOT); }
    public String getTitle() { return title; }
    public String getEstablishmentName() { return establishmentName; }
    public String getAddress() { return address; }
    public int getAmount() { return amount; }
    public int getQuantity() { return quantity; }
    public boolean isPaid() { return "PAID".equals(status); }
    public boolean isCurrent() {
        return ("AWAITING_PAYMENT".equals(status) || isPaid())
                && pickupEnd.isAfter(Instant.now());
    }
    public String getCodeGroups() {
        return pickupCode == null ? "" : pickupCode.substring(0, 3) + " " + pickupCode.substring(3);
    }
    public String getCreatedAtText() { return AppTime.format(createdAt); }
    public String getPickupWindow() {
        return pickupStart.atZone(AppTime.ZONE).format(TIME) + " — " + pickupEnd.atZone(AppTime.ZONE).format(TIME);
    }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }

    public String getStatusText() {
        if (isPickupExpired()) return "Время получения истекло";
        if ("AWAITING_PAYMENT".equals(status)) return "Ожидает оплаты";
        if ("PAID".equals(status)) return "Оплачен";
        if ("RECEIVED".equals(status) || "ISSUED".equals(status) || "PICKED_UP".equals(status)) return "Получен";
        if ("RESERVATION_EXPIRED".equals(cancelReason)) return "Время получения истекло";
        return "Отменен";
    }

    public String getStatusCss() {
        if (isPickupExpired()) return "badge-soft-dark";
        if ("PAID".equals(status)) return "badge-green";
        if ("RECEIVED".equals(status) || "ISSUED".equals(status) || "PICKED_UP".equals(status)) return "badge-green";
        if ("AWAITING_PAYMENT".equals(status)) return "badge-orange";
        return "badge-soft-dark";
    }

    private boolean isPickupExpired() {
        return ("PAID".equals(status) || "AWAITING_PAYMENT".equals(status))
                && !pickupEnd.isAfter(Instant.now());
    }

    public boolean isRefundVisible() { return refundStatus != null && !"NONE".equals(refundStatus); }

    public String getRefundText() {
        return switch (refundStatus) {
            case "PENDING" -> "Возврат ожидает обработки";
            case "COMPLETED" -> "Возврат выполнен";
            case "FAILED" -> "Возврат не выполнен";
            default -> "Возврат не требуется";
        };
    }

    public String getPickupDay() {
        LocalDate date = pickupStart.atZone(AppTime.ZONE).toLocalDate();
        LocalDate today = LocalDate.now(AppTime.ZONE);
        if (date.equals(today)) return "Сегодня";
        if (date.equals(today.plusDays(1))) return "Завтра";
        return date.format(DAY);
    }
}
