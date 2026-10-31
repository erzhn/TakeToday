package com.example.taketoday.payment;

import java.util.UUID;

/**
 * Платёжная служба. Сейчас подключена только учебная {@link com.example.taketoday.payment.mock.MockPaymentGateway};
 * настоящий банк потом подключается новой реализацией этого интерфейса, остальной код не меняется.
 */
public interface PaymentGateway {

    /**
     * Регистрирует платёж у платёжной службы.
     *
     * @return номер платежа у службы и адрес страницы, куда отправить покупателя
     * @throws PaymentUnavailableException служба не отвечает — покупатель может повторить позже
     */
    Checkout start(UUID paymentId, int amount, String description);

    /**
     * Возврат денег по платежу (этап 5).
     *
     * @return номер возврата у платёжной службы
     * @throws PaymentUnavailableException служба не ответила — можно повторить позже
     * @throws RefundDeclinedException     служба отказала — повторять бессмысленно, нужен администратор
     */
    String refund(String providerPaymentId, int amount, String reason);

    record Checkout(String providerPaymentId, String redirectUrl) {
    }
}
