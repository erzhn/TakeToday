package com.example.taketoday.pickup;

/**
 * Цифры за сегодня для кабинета заведения (экран 7) и страницы выдачи.
 *
 * @param paidCount   заказов оплачено сегодня
 * @param paidAmount  выручка за сегодня, сом
 * @param issuedCount выдано сегодня
 * @param waiting     оплачены и ещё ждут покупателя
 */
public record PickupStats(int paidCount, long paidAmount, int issuedCount, int waiting) {
}
