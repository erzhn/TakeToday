package com.example.taketoday.payment;

import com.example.taketoday.auth.ApiError;
import com.example.taketoday.common.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Оплата и код получения — только для покупателя, и только своего заказа (п. 5.2.3 ТЗ). */
@Component
public class CustomerGuard {

    private final CurrentUserService currentUser;

    public CustomerGuard(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public UUID customerId() {
        var user = currentUser.principal()
                .orElseThrow(() -> new ApiError(HttpStatus.UNAUTHORIZED, "Войдите в учетную запись."));
        if (!"CUSTOMER".equals(user.role())) {
            throw new ApiError(HttpStatus.FORBIDDEN, "Оплата доступна только покупателям.");
        }
        return user.id();
    }
}
