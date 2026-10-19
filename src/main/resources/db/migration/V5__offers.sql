-- Общая модель для каталога и будущего кабинета продавца. Деньги — целые сомы.
CREATE TABLE offers (
    id UUID PRIMARY KEY,
    establishment_id UUID NOT NULL REFERENCES establishments(id),
    title VARCHAR(150) NOT NULL CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 150),
    composition TEXT NOT NULL,
    food_type VARCHAR(20) NOT NULL CHECK (food_type IN ('BAKERY','HOT','SALAD','DESSERT','SUSHI','DRINKS')),
    photo_path VARCHAR(255) NOT NULL,
    regular_price INTEGER NOT NULL CHECK (regular_price BETWEEN 1 AND 100000),
    discount_price INTEGER NOT NULL CHECK (discount_price > 0 AND discount_price < regular_price),
    total_portions INTEGER NOT NULL CHECK (total_portions BETWEEN 1 AND 100),
    reserved_portions INTEGER NOT NULL DEFAULT 0 CHECK (reserved_portions >= 0),
    sold_portions INTEGER NOT NULL DEFAULT 0 CHECK (sold_portions >= 0),
    pickup_start TIMESTAMP WITH TIME ZONE NOT NULL,
    pickup_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','WITHDRAWN','HIDDEN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offers_portions_check CHECK (reserved_portions + sold_portions <= total_portions),
    CONSTRAINT offers_pickup_check CHECK (pickup_end > pickup_start)
);
CREATE INDEX offers_catalog ON offers(status, pickup_end);
CREATE INDEX offers_establishment ON offers(establishment_id);
CREATE INDEX offers_type_price ON offers(food_type, discount_price);
