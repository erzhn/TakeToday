package com.example.taketoday.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страница «Как это работает» — отдельная страница с общей шапкой сайта. Раньше пункт меню вёл на главную
 * (/#home), у которой своя шапка: при переходе меню «прыгало», а пункт не подсвечивался.
 */
@Controller
public class HowItWorksController {

    @GetMapping("/how")
    public String how() {
        return "how";
    }
}
