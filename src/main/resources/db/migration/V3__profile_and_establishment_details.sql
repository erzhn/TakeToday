-- Этап 1 (Эржан): личные данные и сведения о заведении (п. 4.2.1, 4.2.2 ТЗ).
-- Дополняет таблицы accounts и establishments из V2__authentication.sql (Жаныбек).

-- Телефон покупателя/сотрудника в личном кабинете
ALTER TABLE accounts ADD COLUMN phone VARCHAR(30);

-- Дополнительные сведения о заведении (экран 13 в Figma)
ALTER TABLE establishments ADD COLUMN category      VARCHAR(30);
ALTER TABLE establishments ADD COLUMN description   VARCHAR(500);
ALTER TABLE establishments ADD COLUMN district      VARCHAR(30);
ALTER TABLE establishments ADD COLUMN contact_email VARCHAR(254);
ALTER TABLE establishments ADD COLUMN instagram     VARCHAR(100);
ALTER TABLE establishments ADD COLUMN logo_path     VARCHAR(255);
ALTER TABLE establishments ADD COLUMN updated_at    TIMESTAMP WITH TIME ZONE;

-- График работы по дням недели: 1 = понедельник ... 7 = воскресенье.
-- Колонка establishments.working_hours остаётся: в неё записывается краткий текст графика
-- («Пн–Пт 09:00–21:00, Сб–Вс выходной»), чтобы его могли показывать другие части сайта.
CREATE TABLE establishment_work_days (
    establishment_id UUID    NOT NULL REFERENCES establishments (id) ON DELETE CASCADE,
    day_of_week      INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    working          BOOLEAN NOT NULL,
    open_time        TIME,
    close_time       TIME,
    PRIMARY KEY (establishment_id, day_of_week)
);
