package com.example.taketoday.orderhistory;

import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = OrderHistoryController.class)
public class OrderHistoryPageAdvice {

    private final ObjectProvider<CurrentUserService> currentUser;

    public OrderHistoryPageAdvice(ObjectProvider<CurrentUserService> currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("headerInfo")
    public CurrentAccount headerInfo() {
        CurrentUserService service = currentUser.getIfAvailable();
        return service == null ? null : service.find().orElse(null);
    }
}
