package com.example.taketoday.payment.mock;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.payment.CustomerGuard;
import com.example.taketoday.payment.Payment;
import com.example.taketoday.payment.PaymentOrder;
import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.payment.PaymentStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Страница учебной платёжной службы (вместо страницы банка). Настоящие деньги не списываются.
 * После «оплаты» вызывает {@link PaymentService#complete} — так же, как это сделало бы уведомление от банка.
 */
@Controller
public class MockPaymentController {

    private final PaymentService payments;
    private final CustomerGuard guard;
    private final Clock clock;

    public MockPaymentController(PaymentService payments, CustomerGuard guard,
                                 @Qualifier("reservationClock") Clock clock) {
        this.payments = payments;
        this.guard = guard;
        this.clock = clock;
    }

    @GetMapping("/mock-pay/{providerId}")
    public String page(@PathVariable String providerId, Model model, HttpServletResponse response) {
        UUID customer = guard.customerId();
        Payment payment = find(providerId, customer);
        if (payment.status() == PaymentStatus.CANCELLED) {
            return "redirect:/reservations/" + payment.orderId();
        }
        if (payment.status() != PaymentStatus.CREATED) {
            return "redirect:/orders/" + payment.orderId();
        }
        response.setHeader("Cache-Control", "no-store");
        fill(model, payment, customer);
        return "payment/mock-pay";
    }

    @PostMapping("/mock-pay/{providerId}")
    public String submit(@PathVariable String providerId,
                         @RequestParam(defaultValue = "pay") String action,
                         @RequestParam(required = false) String cardNumber,
                         @RequestParam(required = false) String expiry,
                         @RequestParam(required = false) String cvv,
                         Model model, RedirectAttributes flash) {
        UUID customer = guard.customerId();
        Payment payment = find(providerId, customer);
        UUID orderId = payment.orderId();
        if (payment.status() == PaymentStatus.CANCELLED) {
            // Открыта старая вкладка: покупатель уже начал новую попытку оплаты
            flash.addFlashAttribute("paymentError", "Эта страница оплаты устарела. Нажмите «Перейти к оплате» ещё раз.");
            return "redirect:/reservations/" + orderId;
        }
        if (payment.status() != PaymentStatus.CREATED) {
            // Повторная отправка формы (двойной щелчок, «Назад» в браузере): результат уже есть,
            // сервис ничего не меняет и только записывает PAYMENT_DUPLICATE в журнал
            payments.complete(providerId, payment.status() == PaymentStatus.SUCCEEDED, payment.cardLast4(), payment.errorMessage());
            return "redirect:/orders/" + orderId;
        }

        PaymentResult result;
        if ("cancel".equals(action)) {
            result = payments.complete(providerId, false, null, "Оплата отменена");
        } else {
            List<String> errors = MockCard.validate(cardNumber, expiry, cvv, YearMonth.from(clock.instant().atZone(AppTime.ZONE)));
            if (!errors.isEmpty()) {
                fill(model, payment, customer);
                model.addAttribute("errors", errors);
                model.addAttribute("cardNumber", cardNumber);
                model.addAttribute("expiry", expiry);
                return "payment/mock-pay";
            }
            String card = MockCard.digits(cardNumber);
            String last4 = card.substring(card.length() - 4);
            result = MockCard.DECLINED_CARD.equals(card)
                    ? payments.complete(providerId, false, last4, "Недостаточно средств на карте")
                    : payments.complete(providerId, true, last4, null);
        }

        if (result == PaymentResult.FAILED) {
            // Страница брони сама покажет причину отказа и кнопку «Попробовать ещё раз»
            return "redirect:/reservations/" + orderId;
        }
        return "redirect:/orders/" + orderId;
    }

    private Payment find(String providerId, UUID customer) {
        return payments.paymentOf(providerId, customer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void fill(Model model, Payment payment, UUID customer) {
        PaymentOrder order = payments.order(payment.orderId(), customer);
        model.addAttribute("payment", payment);
        model.addAttribute("order", order);
        model.addAttribute("serverNow", clock.instant());
        model.addAttribute("successCard", "4242 4242 4242 4242");
        model.addAttribute("declinedCard", "4000 0000 0000 0002");
    }
}
