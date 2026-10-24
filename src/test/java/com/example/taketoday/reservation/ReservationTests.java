package com.example.taketoday.reservation;

import com.example.taketoday.auth.ApiError;
import com.example.taketoday.auth.AuthModels;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${reservation.test.url:jdbc:h2:mem:reservations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000}",
    "spring.datasource.username=${reservation.test.user:sa}",
    "spring.datasource.password=${reservation.test.password:}",
    "spring.datasource.driver-class-name=${reservation.test.driver:org.h2.Driver}",
    "app.admin-email=", "app.admin-password=", "app.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc
class ReservationTests {
    @Autowired JdbcTemplate db;
    @Autowired ReservationService service;
    @Autowired MockMvc mvc;
    @MockitoBean(name = "reservationClock") Clock clock;
    // От текущего времени: карточка набора проверяет окно выдачи по настоящим часам, а не по часам брони
    final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    UUID owner, venue, offer, customer, other;
    List<UUID> accounts;

    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        accounts = new ArrayList<>();
        owner = account("STAFF"); customer = account("CUSTOMER"); other = account("CUSTOMER");
        venue = UUID.randomUUID(); offer = UUID.randomUUID();
        db.update("INSERT INTO establishments(id,owner_id,name,address,contact,working_hours) VALUES(?,?,'Пекарня','Бишкек, Токтогула 1','123','9–23')", venue, owner);
        db.update("""
                INSERT INTO offers(id,establishment_id,title,composition,food_type,photo_path,regular_price,
                    discount_price,total_portions,pickup_start,pickup_end,status)
                VALUES(?,?,'Тестовая выпечка','Мука, яйца','BAKERY','/images/offers/bakery.jpg',500,200,1,?,?,'ACTIVE')
                """, offer, venue, Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now.plusSeconds(7200)));
    }
    @AfterEach void clean() {
        db.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM reservation_orders WHERE offer_id=?)", offer);
        db.update("DELETE FROM reservation_orders WHERE offer_id=?", offer);
        db.update("DELETE FROM offers WHERE id=?", offer);
        db.update("DELETE FROM establishments WHERE id=?", venue);
        for (UUID id : accounts) db.update("DELETE FROM accounts WHERE id=?", id);
    }
    UUID account(String role) {
        UUID id = UUID.randomUUID(); accounts.add(id);
        db.update("INSERT INTO accounts(id,email,password_hash,name,role,verified) VALUES(?,?,'disabled','Тест',?,TRUE)", id, id+"@example.com", role);
        return id;
    }
    int reserved() { return db.queryForObject("SELECT reserved_portions FROM offers WHERE id=?", Integer.class, offer); }
    int orders() { return db.queryForObject("SELECT COUNT(*) FROM reservation_orders WHERE offer_id=?", Integer.class, offer); }
    UsernamePasswordAuthenticationToken auth(UUID id, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthModels.User(id, id+"@example.com", "Тест", role, 0), null,
                List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    String body(UUID key) { return "{\"offerId\":\""+offer+"\",\"requestKey\":\""+key+"\",\"quantity\":1}"; }

    @Test void multiplePortionsKeepQuantityPriceAndReleaseOnlyExpiredQuantity() {
        db.update("UPDATE offers SET total_portions=6 WHERE id=?", offer);
        UUID key = UUID.randomUUID();
        Reservation first = service.create(customer, offer, key, 3);
        assertEquals(3, first.quantity()); assertEquals(200, first.unitPrice());
        assertEquals(600, first.amount()); assertEquals(3, reserved());
        assertEquals(first.id(), service.create(customer, offer, key, 3).id());
        assertThrows(ApiError.class, () -> service.create(customer, offer, key, 2));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        Reservation second = service.create(other, offer, UUID.randomUUID(), 2);
        db.update("UPDATE offers SET discount_price=300 WHERE id=?", offer);
        assertEquals(600, service.get(first.id(), customer).amount());
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        service.expireDue(); service.expireDue();
        assertEquals(2, reserved());
        assertEquals("CANCELLED", service.get(first.id(), customer).status());
        assertEquals("AWAITING_PAYMENT", service.get(second.id(), other).status());
        when(clock.instant()).thenReturn(now.plusSeconds(240));
        service.expireDue(); assertEquals(0, reserved());
    }

    @Test void insufficientInvalidAndOverflowingQuantitiesDoNotChangeStock() {
        for (int quantity : List.of(0, -1, 2, Integer.MAX_VALUE))
            assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID(), quantity));
        assertEquals(0, orders()); assertEquals(0, reserved());
    }

    @Test void quantityApiValidatesAndRendersTotal() throws Exception {
        db.update("UPDATE offers SET total_portions=3 WHERE id=?", offer);
        for (String quantity : List.of("0", "-1", "null", "4")) {
            mvc.perform(post("/api/orders/reservations").with(authentication(auth(customer,"CUSTOMER"))).with(csrf())
                    .contentType("application/json").content(body(UUID.randomUUID()).replace("\"quantity\":1", "\"quantity\":"+quantity)))
                    .andExpect(status().is4xxClientError());
        }
        assertEquals(0, reserved());
        mvc.perform(post("/api/orders/reservations").with(authentication(auth(customer,"CUSTOMER"))).with(csrf())
                .contentType("application/json").content(body(UUID.randomUUID()).replace("\"quantity\":1", "\"quantity\":3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.quantity").value(3))
                .andExpect(jsonPath("$.order.unitPrice").value(200)).andExpect(jsonPath("$.order.amount").value(600));
        UUID id = db.queryForObject("SELECT id FROM reservation_orders WHERE offer_id=?",UUID.class,offer);
        mvc.perform(get("/reservations/"+id).with(authentication(auth(customer,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("600 сом")))
                .andExpect(content().string(containsString("Количество порций")));
    }

    @Test void simultaneousMultiPortionOrdersCannotOversell() throws Exception {
        db.update("UPDATE offers SET total_portions=3 WHERE id=?", offer);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (UUID buyer : List.of(customer, other)) calls.add(() -> {
                start.await();
                try { service.create(buyer, offer, UUID.randomUUID(), 2); return true; }
                catch (ApiError expected) { return false; }
            });
            List<Future<Boolean>> results = new ArrayList<>();
            for (var call : calls) results.add(pool.submit(call));
            start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes); assertEquals(2, reserved()); assertEquals(1, orders());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void holdsOnePortionForExactlyThreeMinutesAndKeepsSnapshot() {
        Reservation order = service.create(customer, offer, UUID.randomUUID());
        assertEquals("AWAITING_PAYMENT", order.status());
        assertEquals(now.plusSeconds(180), order.expiresAt());
        assertEquals(now, order.createdAt());
        assertEquals(200, order.amount()); assertEquals(1, reserved());
        db.update("UPDATE offers SET discount_price=300 WHERE id=?", offer);
        assertEquals(200, service.get(order.id(), customer).amount());
        when(clock.instant()).thenReturn(now.plusSeconds(179));
        assertEquals("AWAITING_PAYMENT", service.get(order.id(), customer).status());
        assertEquals(1, reserved());
    }

    @Test void expiresAtBoundaryOnceAndReturnsLastPortionToSale() {
        UUID key = UUID.randomUUID();
        Reservation order = service.create(customer, offer, key);
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        service.expireDue(); service.expireDue();
        assertEquals("CANCELLED", service.get(order.id(), customer).status());
        assertEquals(0, reserved());
        assertEquals(order.id(), service.create(customer, offer, key).id());
        assertEquals(0, reserved());
        Reservation next = service.create(other, offer, UUID.randomUUID());
        assertNotEquals(order.id(), next.id()); assertEquals(1, reserved());
    }

    @Test void newRequestReclaimsExpiredPortionEvenBeforeBackgroundJob() {
        Reservation first = service.create(customer, offer, UUID.randomUUID());
        when(clock.instant()).thenReturn(now.plusSeconds(181));
        service.create(other, offer, UUID.randomUUID());
        assertEquals("CANCELLED", service.get(first.id(), customer).status());
        assertEquals(1, reserved()); assertEquals(2, orders());
    }

    @Test void repeatedRequestDoesNotCreateAnotherOrderOrExtendDeadline() {
        UUID key = UUID.randomUUID();
        Reservation order = service.create(customer, offer, key);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        Reservation repeated = service.create(customer, offer, key);
        assertEquals(order.id(), repeated.id()); assertEquals(order.expiresAt(), repeated.expiresAt());
        assertEquals(1, orders()); assertEquals(1, reserved());
    }

    @Test void oneHundredConcurrentBuyersCannotReserveTheSameLastPortion() throws Exception {
        List<UUID> buyers = new ArrayList<>();
        for (int n=0;n<100;n++) buyers.add(account("CUSTOMER"));
        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (UUID buyer : buyers) results.add(pool.submit(() -> {
                start.await();
                try { service.create(buyer, offer, UUID.randomUUID()); return true; }
                catch (ApiError expected) { assertTrue(expected.getMessage().contains("порций")); return false; }
            }));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(45, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes); assertEquals(1, orders()); assertEquals(1, reserved());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void concurrentRetriesOfSameRequestAreIdempotent() throws Exception {
        UUID key = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<UUID>> calls = new ArrayList<>();
            for (int n=0;n<10;n++) calls.add(() -> service.create(customer, offer, key).id());
            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> result : pool.invokeAll(calls)) ids.add(result.get());
            assertEquals(1, ids.size()); assertEquals(1, orders()); assertEquals(1, reserved());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void concurrentExpiryWorkersAndNextBuyerDoNotReleasePortionTwice() throws Exception {
        Reservation first = service.create(customer, offer, UUID.randomUUID());
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int n=0;n<7;n++) calls.add(() -> { service.expireDue(); return null; });
            calls.add(() -> { service.create(other, offer, UUID.randomUUID()); return null; });
            for (Future<Void> result : pool.invokeAll(calls)) result.get();
            assertEquals("CANCELLED", service.get(first.id(), customer).status());
            assertEquals(1, reserved()); assertEquals(2, orders());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void rejectedCustomersCannotReserveEvenThroughService() {
        assertThrows(ApiError.class, () -> service.create(owner, offer, UUID.randomUUID()));
        db.update("UPDATE accounts SET verified=FALSE WHERE id=?",customer);
        assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        db.update("UPDATE accounts SET verified=TRUE,blocked=TRUE WHERE id=?",customer);
        assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        assertEquals(0,orders()); assertEquals(0,reserved());
    }

    @Test void refusesUnavailableExpiredAndBlockedOffersWithoutCreatingOrders() {
        for (String status : List.of("WITHDRAWN", "HIDDEN")) {
            db.update("UPDATE offers SET status=? WHERE id=?", status, offer);
            assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        }
        db.update("UPDATE offers SET status='ACTIVE',pickup_start=?,pickup_end=? WHERE id=?",
                Timestamp.from(now.minusSeconds(3600)),Timestamp.from(now),offer);
        assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        db.update("UPDATE offers SET pickup_end=? WHERE id=?",Timestamp.from(now.plusSeconds(7200)),offer);
        db.update("UPDATE accounts SET blocked=TRUE WHERE id=?",owner);
        assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        assertEquals(0, orders()); assertEquals(0, reserved());
    }

    @Test void soldPortionsAreNeverAvailableForBooking() {
        db.update("UPDATE offers SET sold_portions=1 WHERE id=?",offer);
        assertThrows(ApiError.class, () -> service.create(customer, offer, UUID.randomUUID()));
        assertEquals(0, orders()); assertEquals(0, reserved());
    }

    @Test void confirmedPaymentIsNotReleasedByExpiryJob() {
        Reservation order = service.create(customer,offer,UUID.randomUUID());
        // Будущий платежный модуль обязан менять статус и оба счетчика одной транзакцией.
        db.update("UPDATE reservation_orders SET status='PAID' WHERE id=?",order.id());
        db.update("UPDATE offers SET reserved_portions=0,sold_portions=1 WHERE id=?",offer);
        when(clock.instant()).thenReturn(now.plusSeconds(181));
        service.expireDue();
        assertEquals("PAID",service.get(order.id(),customer).status());
        assertEquals(0,reserved());
        assertEquals(1,db.queryForObject("SELECT sold_portions FROM offers WHERE id=?",Integer.class,offer));
    }

    @Test void offerPageEnablesBookingOnlyWhenPortionsRemain() throws Exception {
        String active = mvc.perform(get("/offers/"+offer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var activeButton = java.util.regex.Pattern.compile("<button[^>]*data-reserve-offer[^>]*>").matcher(active);
        assertTrue(activeButton.find()); assertFalse(activeButton.group().contains("disabled"));
        db.update("UPDATE offers SET sold_portions=1 WHERE id=?",offer);
        String sold = mvc.perform(get("/offers/"+offer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var soldButton = java.util.regex.Pattern.compile("<button[^>]*data-reserve-offer[^>]*>").matcher(sold);
        assertTrue(soldButton.find()); assertTrue(soldButton.group().contains("disabled"));
    }

    @Test void apiChecksRoleCsrfAndOwnershipAndRendersCheckout() throws Exception {
        String payload = body(UUID.randomUUID());
        mvc.perform(post("/api/orders/reservations").with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/reservations").with(authentication(auth(owner,"STAFF"))).with(csrf())
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/reservations").with(authentication(auth(customer,"CUSTOMER")))
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/reservations").with(authentication(auth(customer,"CUSTOMER"))).with(csrf())
                .contentType("application/json").content(payload)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.order.amount").value(200));
        UUID id = db.queryForObject("SELECT id FROM reservation_orders WHERE offer_id=?",UUID.class,offer);
        mvc.perform(get("/api/orders/reservations/"+id).with(authentication(auth(other,"CUSTOMER"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reservations/"+id).with(authentication(auth(other,"CUSTOMER"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reservations/"+id).with(authentication(auth(owner,"STAFF"))))
                .andExpect(status().isForbidden());
        var checkout = mvc.perform(get("/reservations/"+id).with(authentication(auth(customer,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(containsString("Бишкек, Токтогула 1")))
                .andExpect(content().string(containsString("200 сом")))
                .andExpect(content().string(containsString("Перейти к оплате")))
                .andReturn();
        assertEquals(1, java.util.regex.Pattern.compile("<footer\\b").matcher(checkout.getResponse().getContentAsString()).results().count());
        // Снимок реального Thymeleaf-ответа для локальной проверки верстки; не входит в исходники.
        var preview = java.nio.file.Path.of("target", "reservation-preview.html");
        java.nio.file.Files.createDirectories(preview.getParent());
        java.nio.file.Files.writeString(preview, checkout.getResponse().getContentAsString()
                .replace("<head>", "<head><base href=\"http://localhost:8080/\">"));
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        mvc.perform(get("/api/orders/reservations/"+id).with(authentication(auth(customer,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }
}
