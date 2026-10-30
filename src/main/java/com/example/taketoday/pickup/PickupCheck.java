package com.example.taketoday.pickup;

/**
 * Результат проверки кода (п. 4.7.4 ТЗ).
 *
 * @param order   найденный заказ (null, если код не найден или заказ чужой)
 * @param error   почему выдать нельзя; null — можно выдавать
 * @param warning не мешает выдаче, но сотрудник должен увидеть (например, окно выдачи ещё не началось)
 */
public record PickupCheck(PickupOrder order, String error, String warning) {

    public static PickupCheck ok(PickupOrder order, String warning) {
        return new PickupCheck(order, null, warning);
    }

    public static PickupCheck refused(PickupOrder order, String error) {
        return new PickupCheck(order, error, null);
    }

    public boolean isCanIssue() {
        return error == null;
    }
}
