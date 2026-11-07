-- Этап 5 (Эржан): отмена заказа, возврат денег, админ-панель (п. 4.6.4, 4.6.5, 4.10, 5.3.4 ТЗ).

-- Возвраты. На один платёж — не больше одного незавершённого или выполненного возврата
-- (проверяет RefundService под блокировкой заказа). FAILED можно повторить вручную.
CREATE TABLE refunds (
    id                 UUID         PRIMARY KEY,
    order_id           UUID         NOT NULL REFERENCES reservation_orders(id),
    payment_id         UUID         NOT NULL REFERENCES payments(id),
    amount             INTEGER      NOT NULL CHECK (amount > 0),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    -- AUTO — система (отмена покупателем, заведением, поздняя оплата); ADMIN — вручную в админ-панели
    initiated_by       VARCHAR(10)  NOT NULL CHECK (initiated_by IN ('AUTO', 'ADMIN')),
    admin_id           UUID         REFERENCES accounts(id),
    reason             VARCHAR(255) NOT NULL,
    provider_refund_id VARCHAR(64)  UNIQUE,
    error_message      VARCHAR(255),
    -- Сколько раз обращались к платёжной службе (если она недоступна — повторяем, после 3 раз FAILED)
    attempts           INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at       TIMESTAMP WITH TIME ZONE
);
CREATE INDEX refunds_order ON refunds(order_id);
CREATE INDEX refunds_status ON refunds(status);

-- Когда и кто отменил заказ
ALTER TABLE reservation_orders ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reservation_orders ADD COLUMN cancelled_by UUID REFERENCES accounts(id);
ALTER TABLE reservation_orders ADD COLUMN cancel_comment VARCHAR(255);

-- Блокировка учётной записи администратором: когда и за что
ALTER TABLE accounts ADD COLUMN blocked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN blocked_reason VARCHAR(255);

-- Скрытие предложения администратором: за что (видит сотрудник заведения)
ALTER TABLE offers ADD COLUMN hidden_reason VARCHAR(255);

CREATE INDEX payments_created ON payments(created_at);
