package com.example.taketoday.payment;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.taketoday.refund.RefundService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Экран 4 «Заказ оплачен — код получения» и кнопка «Перейти к оплате» на странице брони (экран 3).
 */
@Controller
public class OrderController {

    private final PaymentService payments;
    private final CustomerGuard guard;
    private final RefundService refunds;
    private final Clock clock;

    public OrderController(PaymentService payments, CustomerGuard guard, RefundService refunds,
                           @Qualifier("reservationClock") Clock clock) {
        this.payments = payments;
        this.guard = guard;
        this.refunds = refunds;
        this.clock = clock;
    }

    /** «Перейти к оплате»: создаём попытку оплаты и отправляем покупателя на страницу платёжной службы. */
    @PostMapping("/reservations/{id}/pay")
    public String pay(@PathVariable UUID id, RedirectAttributes flash) {
        UUID customer = guard.customerId();
        try {
            return "redirect:" + payments.start(id, customer);
        } catch (PaymentException e) {
            flash.addFlashAttribute("paymentError", e.getMessage());
            return "redirect:/reservations/" + id;
        }
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable UUID id, Model model, HttpServletResponse response) {
        PaymentOrder order = payments.order(id, guard.customerId());
        if (!order.hasOrderPage()) {
            // Ещё не оплачен или бронь просто истекла — это показывает страница брони Жаныбека
            return "redirect:/reservations/" + id;
        }
        Instant now = clock.instant();
        response.setHeader("Cache-Control", "no-store"); // код получения не должен оставаться в кэше
        model.addAttribute("order", order);
        model.addAttribute("payment", payments.paymentForReceipt(id).orElse(null));
        model.addAttribute("refunds", refunds.forOrder(id));
        model.addAttribute("canCancel", order.canCancel(now));
        model.addAttribute("pickupOver", order.isPickupOver(now));
        return "orders/detail";
    }

    /** QR-код — только владельцу и только оплаченного заказа. */
    @GetMapping("/orders/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable UUID id) {
        PaymentOrder order = payments.order(id, guard.customerId());
        if (!order.isPaid() || order.isPickupOver(clock.instant())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(QrCodeImage.png(QrCodeImage.content(order), 320));
    }
}
