-- Один заказ закрепляет одну порцию. Снимки цены и места выдачи сохраняются при оформлении.
CREATE TABLE reservation_orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES accounts(id),
    offer_id UUID NOT NULL REFERENCES offers(id),
    request_key UUID NOT NULL,
    title VARCHAR(150) NOT NULL,
    establishment_name VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    pickup_start TIMESTAMP WITH TIME ZONE NOT NULL,
    pickup_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AWAITING_PAYMENT'
        CHECK (status IN ('AWAITING_PAYMENT', 'PAID', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT reservation_request_unique UNIQUE (customer_id, request_key),
    CONSTRAINT reservation_expiry_check CHECK (expires_at > created_at)
);
CREATE INDEX reservation_expiry ON reservation_orders(status, expires_at);
CREATE INDEX reservation_offer ON reservation_orders(offer_id);
