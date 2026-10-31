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
    private final boolean refundsAvailable;

    /**
     * @param refundsAvailable false — служба «не отвечает» на возвраты: так проверяется повтор и ручной возврат
     */
    public MockPaymentGateway(@Value("${app.payment.mock.available:true}") boolean available,
                              @Value("${app.payment.mock.refunds-available:true}") boolean refundsAvailable) {
        this.available = available;
        this.refundsAvailable = refundsAvailable;
    }

    @Override
    public Checkout start(UUID paymentId, int amount, String description) {
        if (!available) {
            throw new PaymentUnavailableException("Учебная платёжная служба выключена настройкой app.payment.mock.available");
        }
        String providerId = "mock_" + UUID.randomUUID().toString().replace("-", "");
        return new Checkout(providerId, "/mock-pay/" + providerId);
    }

    @Override
    public String refund(String providerPaymentId, int amount, String reason) {
        if (!available || !refundsAvailable) {
            throw new PaymentUnavailableException("Учебная платёжная служба не принимает возвраты (app.payment.mock.refunds-available)");
        }
        if (providerPaymentId == null || !providerPaymentId.startsWith("mock_") || amount <= 0) {
            throw new com.example.taketoday.payment.RefundDeclinedException("Платёж не найден у платёжной службы");
        }
        return "mockrefund_" + UUID.randomUUID().toString().replace("-", "");
    }
}
