package com.example.taketoday.common;

import com.example.taketoday.admin.AdminController;
import com.example.taketoday.profile.ProfileController;
import com.example.taketoday.venue.StaffController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Добавляет во все страницы кабинета переменную ${headerInfo} — данные для шапки (имя, роль, заведение).
 * Подключена только к контроллерам страниц (и страницам ошибок 401/403/404/500), чтобы не делать лишний запрос к базе в каждом REST-вызове.
 */
@ControllerAdvice(assignableTypes = {ProfileController.class, StaffController.class, AdminController.class,
        ErrorController.class})
public class CurrentUserAdvice {

    /** Через ObjectProvider: в тестах @WebMvcTest сервиса нет, и шапка тогда просто гостевая. */
    private final ObjectProvider<CurrentUserService> currentUser;

    public CurrentUserAdvice(ObjectProvider<CurrentUserService> currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("headerInfo")
    public CurrentAccount headerInfo() {
        CurrentUserService service = currentUser.getIfAvailable();
        return service == null ? null : service.find().orElse(null);
    }
}
