package com.example.taketoday.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PasswordRecoveryPageController {
    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "redirect:/index.html#recovery";
    }
}
