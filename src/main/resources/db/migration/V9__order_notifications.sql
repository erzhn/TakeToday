CREATE TABLE order_notifications (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES reservation_orders(id),
    type VARCHAR(40) NOT NULL,
    event_key VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 1 CHECK (attempts > 0),
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT order_notifications_unique UNIQUE (order_id, event_key)
);

CREATE INDEX order_notifications_status ON order_notifications(status);
