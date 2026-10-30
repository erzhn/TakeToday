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

    @GetMapping("/staff/pickup")
    public String pickup(Model model) {
        return page(model, "Выдача заказа", "qr_code_scanner",
                "Здесь сотрудник будет проверять шестизначный код или QR-код покупателя и выдавать заказ.",
                "этап 4 (до 15 декабря)", "/staff");
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
