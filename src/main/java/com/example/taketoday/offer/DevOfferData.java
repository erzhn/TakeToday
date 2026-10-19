package com.example.taketoday.offer;

import com.example.taketoday.common.AppTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

/** Только dev. Не изменяет существующие строки и не создаёт доступные для входа учётки. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.catalog.seed-demo", havingValue = "true", matchIfMissing = true)
public class DevOfferData implements ApplicationRunner {
    private final JdbcTemplate db;
    public DevOfferData(JdbcTemplate db) { this.db = db; }
    private UUID id(int n) { return UUID.fromString("de000000-0000-4000-8000-" + String.format("%012d", n)); }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (int n = 1; n <= 3; n++) {
            if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE id=?", Integer.class, id(n)) == 0)
                db.update("INSERT INTO accounts(id,email,password_hash,name,role,verified) VALUES(?,?,?,?,?,FALSE)",
                        id(n), "catalog-demo-" + n + "@example.invalid", "!disabled-demo-account", "Демо-заведение " + n, "STAFF");
            if (db.queryForObject("SELECT COUNT(*) FROM establishments WHERE id=?", Integer.class, id(10+n)) == 0)
                db.update("INSERT INTO establishments(id,owner_id,name,address,contact,working_hours) VALUES(?,?,?,?,?,?)",
                        id(10+n), id(n), new String[]{"Пекарня «Тёплый хлеб»", "Кафе «Зелёная тарелка»", "Кафе «Вечерний набор»"}[n-1],
                        "Бишкек, ул. Токтогула, " + (100+n), "+996 555 000 00" + n, "Ежедневно 09:00–23:00");
        }
        String[] titles = {"Набор свежей выпечки", "Горячий ужин", "Овощной салат", "Десерты к чаю", "Суши-набор", "Домашний лимонад", "Выпечка — порции закончились", "Снятый набор", "Вчерашний набор", "Скрытый набор"};
        String[] composition = {"Круассан, булочка с корицей и два пирожка. Содержит пшеницу, молоко и яйца.", "Рис, запечённая курица и овощи. Содержит соевый соус.", "Помидоры, огурцы, листья салата, оливковое масло. Соус отдельно.", "Два кусочка чизкейка и маффин. Содержит молоко, яйца и пшеницу.", "Восемь роллов с огурцом и лососем, имбирь и соевый соус. Содержит рыбу и сою.", "Две бутылки лимонада по 0,5 л: лимон и мята."};
        FoodType[] types = FoodType.values();
        for (int n = 0; n < titles.length; n++) {
            UUID offerId = id(100+n);
            if (db.queryForObject("SELECT COUNT(*) FROM offers WHERE id=?", Integer.class, offerId) != 0) continue;
            int type = n < 6 ? n : 0;
            LocalDate date = LocalDate.now(AppTime.ZONE).plusDays(n == 8 ? -1 : n % 2);
            db.update("""
                INSERT INTO offers(id,establishment_id,title,composition,food_type,photo_path,regular_price,
                    discount_price,total_portions,sold_portions,pickup_start,pickup_end,status)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, offerId, id(11+n%3), titles[n], composition[type], types[type].name(),
                    "/images/offers/" + types[type].name().toLowerCase(java.util.Locale.ROOT) + ".jpg",
                    500+type*100, 200+type*50, 5, n == 6 ? 5 : 0,
                    Timestamp.from(date.atTime(17+type%3, 0).atZone(AppTime.ZONE).toInstant()),
                    Timestamp.from(date.atTime(23, 59).atZone(AppTime.ZONE).toInstant()),
                    n == 7 ? "WITHDRAWN" : n == 9 ? "HIDDEN" : "ACTIVE");
        }
    }
}
