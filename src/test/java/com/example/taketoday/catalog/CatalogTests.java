package com.example.taketoday.catalog;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.offer.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "app.admin-email=", "app.admin-password="
})
@AutoConfigureMockMvc
@Transactional
class CatalogTests {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired CatalogService service;
    UUID owner, venue, bread;
    final Instant now = LocalDate.now(AppTime.ZONE).atTime(12, 0).atZone(AppTime.ZONE).toInstant();

    @BeforeEach void prepare() {
        owner = UUID.randomUUID(); venue = UUID.randomUUID();
        db.update("INSERT INTO accounts(id,email,password_hash,name,role,verified) VALUES(?,?,'disabled','Тест','STAFF',TRUE)", owner, owner+"@example.com");
        db.update("INSERT INTO establishments(id,owner_id,name,address,contact,working_hours) VALUES(?,?,'Пекарня','Бишкек, Токтогула 1','123','9–23')", venue, owner);
        bread = offer("Свежая выпечка", "BAKERY", 200, "ACTIVE", 5, 1, 1, 0, 17, 21);
    }
    UUID offer(String title, String type, int price, String status, int total, int reserved, int sold, int day, int start, int end) {
        UUID id = UUID.randomUUID(); LocalDate date = now.atZone(AppTime.ZONE).toLocalDate().plusDays(day);
        db.update("""
            INSERT INTO offers(id,establishment_id,title,composition,food_type,photo_path,regular_price,discount_price,
                total_portions,reserved_portions,sold_portions,pickup_start,pickup_end,status)
            VALUES(?,?,?,'Мука, молоко, яйца',?,'/images/offers/bakery.jpg',1000,?,?,?,?,?,?,?)
            """, id, venue, title, type, price, total, reserved, sold,
                Timestamp.from(date.atTime(start,0).atZone(AppTime.ZONE).toInstant()),
                Timestamp.from(date.atTime(end,0).atZone(AppTime.ZONE).toInstant()), status);
        return id;
    }
    List<Offer> find(CatalogFilter filter) { return service.find(filter, now); }

    @Test void combinedFiltersUseDiscountPriceAndCaseInsensitiveName() {
        offer("Дорогая выпечка", "BAKERY", 600,"ACTIVE",5,0,0,0,17,21);
        offer("Свежий салат", "SALAD", 200,"ACTIVE",5,0,0,0,17,21);
        var f = new CatalogFilter(); f.setQ("ВЫПЕЧКА"); f.setType(FoodType.BAKERY);
        f.setMinPrice(200); f.setMaxPrice(200); f.setPickupAfter(LocalTime.of(20,0)); f.setPickupBefore(LocalTime.of(22,0));
        assertEquals(List.of(bread), find(f).stream().map(Offer::id).toList());
        assertEquals(3, service.getPublic(bread).getAvailablePortions());
    }
    @Test void catalogExcludesHiddenWithdrawnExpiredAndLaterDaysButKeepsSoldOut() {
        for (String status : List.of("HIDDEN","WITHDRAWN")) offer(status,"BAKERY",100,status,5,0,0,0,17,21);
        offer("Вчера", "HOT",100,"ACTIVE",5,0,0,-1,17,21);
        offer("Через неделю", "HOT",100,"ACTIVE",5,0,0,7,17,21);
        UUID soldOut = offer("Всё продано", "BAKERY",100,"ACTIVE",5,2,3,1,17,21);
        assertEquals(Set.of(bread,soldOut), new HashSet<>(find(new CatalogFilter()).stream().map(Offer::id).toList()));
        assertEquals("Порции закончились", service.getPublic(soldOut).availabilityAt(now));
    }
    @Test void timeWindowOverlapsPickupAndUsesBishkek() {
        var f = new CatalogFilter(); f.setPickupAfter(LocalTime.of(20,0)); f.setPickupBefore(LocalTime.of(22,0));
        assertEquals(1,find(f).size());
        f.setPickupAfter(LocalTime.of(21,0)); assertTrue(find(f).isEmpty());
        f.setPickupAfter(LocalTime.of(8,0)); f.setPickupBefore(LocalTime.of(10,0)); assertTrue(find(f).isEmpty());
        f.setPickupAfter(null); f.setPickupBefore(LocalTime.of(17,0)); assertEquals(1,find(f).size());
        assertTrue(service.getPublic(bread).getPickupStartText().endsWith("17:00"));
    }
    @Test void elapsedFilterWindowDoesNotMatchLongRunningOffer() {
        offer("Целый день", "HOT",100,"ACTIVE",5,0,0,0,8,23);
        var f = new CatalogFilter(); f.setPickupBefore(LocalTime.of(10,0));
        assertTrue(find(f).isEmpty());
    }
    @Test void nameWildcardsAreLiteralAndSqlIsParameterized() {
        for(String query : List.of("%", "_", "!", "' OR 1=1 --")) {
            var f = new CatalogFilter(); f.setQ(query); assertTrue(find(f).isEmpty());
        }
        UUID literal = offer("Скидка 50%", "HOT",100,"ACTIVE",5,0,0,0,17,21);
        var f = new CatalogFilter(); f.setQ("50%"); assertEquals(literal,find(f).get(0).id());
    }
    @Test void paginationIsStableAndHasLookahead() {
        for(int n=0;n<25;n++) offer("Набор " + n,"BAKERY",200,"ACTIVE",5,0,0,0,17,21);
        var f = new CatalogFilter(); var first = find(f);
        assertEquals(13, first.size()); f.setPage(1); var second = find(f);
        assertEquals(13, second.size());
        assertTrue(Collections.disjoint(first.subList(0,12).stream().map(Offer::id).toList(), second.subList(0,12).stream().map(Offer::id).toList()));
        f.setPage(2); assertEquals(2,find(f).size());
    }
    @Test void guestCanRenderCatalogAndDetailsWithDatabaseValues() throws Exception {
        mvc.perform(get("/catalog")).andExpect(status().isOk()).andExpect(content().string(containsString("Хорошая еда ждёт вас")));
        mvc.perform(get("/offers/"+bread)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Свежая выпечка")))
                .andExpect(content().string(containsString("Мука, молоко, яйца")))
                .andExpect(content().string(containsString("Бишкек, Токтогула 1")))
                .andExpect(content().string(containsString("200 сом")))
                .andExpect(content().string(containsString("Доступных порций:")));
        mvc.perform(get("/images/offers/bakery.jpg")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/jpeg"));
    }
    @Test void invalidFiltersReturnHelpfulPageInsteadOfServerError() throws Exception {
        String[][] filters = {{"type","INVALID"},{"maxPrice","text"},{"minPrice","-1"},{"pickupBefore","25:00"},{"page","-1"},{"page","999999999999"},{"q","x".repeat(151)}};
        for (String[] filter : filters) mvc.perform(get("/catalog").param(filter[0],filter[1])).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Проверьте фильтры")));
        mvc.perform(get("/catalog").param("minPrice","500").param("maxPrice","100")).andExpect(status().isBadRequest());
        mvc.perform(get("/catalog").param("pickupAfter","21:00").param("pickupBefore","19:00")).andExpect(status().isBadRequest());
    }
    @Test void unavailableDetailsAndBlockedOwnersRespectVisibility() throws Exception {
        UUID hidden = offer("Скрытый", "HOT",100,"HIDDEN",5,0,0,0,17,21);
        UUID withdrawn = offer("Снятый", "HOT",100,"WITHDRAWN",5,0,0,0,17,21);
        UUID expired = offer("Просроченный", "HOT",100,"ACTIVE",5,0,0,-1,17,21);
        mvc.perform(get("/offers/"+hidden)).andExpect(status().isNotFound());
        mvc.perform(get("/offers/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/offers/"+withdrawn)).andExpect(status().isOk()).andExpect(content().string(containsString("Предложение снято с продажи")));
        mvc.perform(get("/offers/"+expired)).andExpect(status().isOk()).andExpect(content().string(containsString("Время выдачи завершилось")));
        db.update("UPDATE accounts SET blocked=TRUE WHERE id=?",owner);
        assertTrue(find(new CatalogFilter()).isEmpty());
        mvc.perform(get("/offers/"+bread)).andExpect(status().isNotFound());
        mvc.perform(post("/offers/"+bread+"/reserve")).andExpect(status().isForbidden());
    }
    @Test void databaseRejectsInconsistentPricesPortionsTimesAndMissingVenue() {
        assertThrows(DataIntegrityViolationException.class, () -> db.update("UPDATE offers SET discount_price=regular_price WHERE id=?",bread));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("UPDATE offers SET reserved_portions=100 WHERE id=?",bread));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("UPDATE offers SET pickup_end=pickup_start WHERE id=?",bread));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("UPDATE offers SET establishment_id=? WHERE id=?",UUID.randomUUID(),bread));
    }
    @Test void demoSeedIsIdempotentAndPreservesEditedOffers() {
        var demo = new DevOfferData(db);
        demo.run(null);
        int count = db.queryForObject("SELECT COUNT(*) FROM offers",Integer.class);
        UUID id=UUID.fromString("de000000-0000-4000-8000-000000000100");
        db.update("UPDATE offers SET title='Изменённый набор' WHERE id=?",id);
        demo.run(null);
        assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM offers",Integer.class));
        assertEquals("Изменённый набор",service.getPublic(id).title());
    }
}
