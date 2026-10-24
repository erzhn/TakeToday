package com.example.taketoday.payment.mock;

import com.example.taketoday.payment.PaymentGateway;
import com.example.taketoday.payment.PaymentUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Учебная платёжная служба внутри приложения: настоящих денег нет.
 * Страница оплаты — {@link MockPaymentController}. Чтобы проверить сценарий «служба недоступна»,
 * запустите приложение с {@code app.payment.mock.available=false}.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final boolean available;

    public MockPaymentGateway(@Value("${app.payment.mock.available:true}") boolean available) {
        this.available = available;
    }

    @Override
    public Checkout start(UUID paymentId, int amount, String description) {
        if (!available) {
            throw new PaymentUnavailableException("Учебная платёжная служба выключена настройкой app.payment.mock.available");
        }
        String providerId = "mock_" + UUID.randomUUID().toString().replace("-", "");
        return new Checkout(providerId, "/mock-pay/" + providerId);
    }
}
