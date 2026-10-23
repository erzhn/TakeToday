-- Существующие заказы сохраняют прежние количество и стоимость.
ALTER TABLE reservation_orders ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0);
ALTER TABLE reservation_orders ADD COLUMN unit_price INTEGER;
UPDATE reservation_orders SET unit_price=amount;
ALTER TABLE reservation_orders ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE reservation_orders ADD CONSTRAINT reservation_unit_price_positive CHECK (unit_price > 0);
ALTER TABLE reservation_orders ADD CONSTRAINT reservation_total_matches_quantity
    CHECK (CAST(amount AS BIGINT) = CAST(unit_price AS BIGINT) * quantity);
