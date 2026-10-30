package com.example.taketoday.common;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Разделы следующих этапов, на которые уже есть ссылки в шапке и меню.
 * Вместо ошибки 404 показываем понятную страницу «Раздел появится на этапе N».
 * Когда раздел будет готов, его адрес нужно убрать отсюда — иначе Spring сообщит о двух обработчиках.
 */
@Controller
public class ComingSoonController {

    @GetMapping("/orders")
    public String buyerOrders(Model model) {
        return page(model, "Мои заказы", "receipt_long",
                "Здесь будут ваши текущие и прошлые заказы с кодом получения.", "этап 5 (до 30 декабря)", "/");
    }

    @GetMapping("/staff/orders")
    public String staffOrders(Model model) {
        return page(model, "Заказы заведения", "receipt_long",
                "Здесь будут все заказы вашего заведения: оплаченные, выданные и отменённые.",
                "этап 5 (до 30 декабря)", "/staff");
    }

    private static String page(Model model, String title, String icon, String text, String stage, String back) {
        model.addAttribute("title", title);
        model.addAttribute("icon", icon);
        model.addAttribute("text", text);
        model.addAttribute("stage", stage);
        model.addAttribute("back", back);
        return "coming-soon";
    }
}
