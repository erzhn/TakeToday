package com.example.taketoday.orderhistory;

import com.example.taketoday.common.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class OrderHistoryController {

    private final OrderHistoryService orders;
    private final CurrentUserService currentUser;

    public OrderHistoryController(OrderHistoryService orders, CurrentUserService currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @GetMapping("/orders")
    public String customerOrders(Model model) {
        List<OrderHistoryEntry> all = orders.customerOrders(currentUser.require().getId());
        model.addAttribute("currentOrders", all.stream().filter(OrderHistoryEntry::isCurrent).toList());
        model.addAttribute("completedOrders", all.stream().filter(order -> !order.isCurrent()).toList());
        return "orders/list";
    }

    @GetMapping("/staff/orders")
    public String staffOrders(Model model) {
        model.addAttribute("orders", orders.staffOrders(currentUser.require().getId()));
        return "orders/staff-list";
    }
}
