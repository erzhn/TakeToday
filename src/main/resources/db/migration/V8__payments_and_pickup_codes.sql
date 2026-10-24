-- Этап 3 (Эржан): оплата, код получения, журнал событий (п. 4.6, 4.7.1, 5.2.2, 5.3.2, 5.3.4 ТЗ).
-- Заказы — таблица reservation_orders Жаныбека (V6, V7); здесь только дополнения.

-- Код получения появляется только после оплаты. Ведущие нули важны, поэтому строка.
ALTER TABLE reservation_orders ADD COLUMN pickup_code VARCHAR(6);
ALTER TABLE reservation_orders ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE;
-- Почему заказ отменён: RESERVATION_EXPIRED, LATE_PAYMENT, CUSTOMER, VENUE, ADMIN
ALTER TABLE reservation_orders ADD COLUMN cancel_reason VARCHAR(30);
-- Возврат денег (этап 5): NONE — не нужен, PENDING — нужно вернуть, COMPLETED, FAILED
ALTER TABLE reservation_orders ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE reservation_orders ADD CONSTRAINT reservation_refund_status_check
    CHECK (refund_status IN ('NONE', 'PENDING', 'COMPLETED', 'FAILED'));
ALTER TABLE reservation_orders ADD CONSTRAINT reservation_pickup_code_check
    CHECK (pickup_code IS NULL OR CHAR_LENGTH(pickup_code) = 6);
CREATE INDEX reservation_pickup_code ON reservation_orders(pickup_code);

-- Попытки оплаты. Полный номер карты и CVV не храним никогда — только последние 4 цифры (п. 5.2.2).
CREATE TABLE payments (
    id                  UUID         PRIMARY KEY,
    order_id            UUID         NOT NULL REFERENCES reservation_orders(id),
    amount              INTEGER      NOT NULL CHECK (amount > 0),
    -- CREATED — ждём результат; SUCCEEDED, FAILED — итог; CANCELLED — заменена новой попыткой
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('CREATED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    -- Номер платежа у платёжной службы: по нему узнаём повторные сообщения об одной оплате (п. 5.3.2)
    provider_payment_id VARCHAR(64)  NOT NULL UNIQUE,
    card_last4          VARCHAR(4),
    error_message       VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE
);
CREATE INDEX payments_order ON payments(order_id);

-- Журнал важных событий: оплата, ошибки, поздняя оплата, повторные сообщения (п. 5.3.4)
CREATE TABLE audit_log (
    id         UUID          PRIMARY KEY,
    event_type VARCHAR(40)   NOT NULL,
    order_id   UUID,
    payment_id UUID,
    user_id    UUID,
    details    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX audit_log_order ON audit_log(order_id);
CREATE INDEX audit_log_created ON audit_log(created_at);
