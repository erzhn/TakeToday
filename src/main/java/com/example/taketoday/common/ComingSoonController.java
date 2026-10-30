package com.example.taketoday.common;

import org.springframework.stereotype.Controller;

/**
 * Разделы следующих этапов, на которые уже есть ссылки в шапке и меню.
 * Вместо ошибки 404 показываем понятную страницу «Раздел появится на этапе N».
 * Когда раздел будет готов, его адрес нужно убрать отсюда — иначе Spring сообщит о двух обработчиках.
 */
@Controller
public class ComingSoonController {

}
