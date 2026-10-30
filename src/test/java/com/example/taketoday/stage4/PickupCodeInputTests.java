package com.example.taketoday.stage4;

import com.example.taketoday.pickup.PickupCodeInput;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Что может прийти в поле кода: цифры с пробелами или содержимое QR-кода. */
class PickupCodeInputTests {

    @Test
    void digitsAndQrAreRecognized() {
        assertEquals("482913", PickupCodeInput.parse("482 913").orElseThrow().code());
        assertEquals("012345", PickupCodeInput.parse(" 012-345 ").orElseThrow().code(), "ведущий ноль сохраняется");
        UUID id = UUID.randomUUID();
        PickupCodeInput qr = PickupCodeInput.parse("TAKETODAY:" + id + ":482913").orElseThrow();
        assertEquals(id, qr.orderId());
        assertEquals("482913", qr.code());
    }

    @Test
    void anythingElseIsRejected() {
        assertTrue(PickupCodeInput.parse(null).isEmpty());
        assertTrue(PickupCodeInput.parse("").isEmpty());
        assertTrue(PickupCodeInput.parse("48291").isEmpty());
        assertTrue(PickupCodeInput.parse("4829133").isEmpty());
        assertTrue(PickupCodeInput.parse("https://example.com").isEmpty());
        assertTrue(PickupCodeInput.parse("TAKETODAY:not-an-id:482913").isEmpty());
    }
}
