package com.example.taketoday;

import com.example.taketoday.auth.AuthService;
import com.example.taketoday.auth.HomeController;
import com.example.taketoday.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = HomeController.class, properties = "app.frontend-origin=http://localhost:5173")
@Import(SecurityConfig.class)
class HomePageTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService service;
    @MockitoBean JdbcTemplate db;

    @Test void guestCanOpenHomePage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>TakeToday")));
        mvc.perform(get("/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
    }

    @Test void privateApiStillRequiresAuthentication() throws Exception {
        for (String path : new String[]{"/api/customer/home", "/api/staff/home", "/api/admin/home", "/api/orders", "/staff", "/admin"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test void panelsRejectOtherRoles() throws Exception {
        mvc.perform(get("/api/admin/home").with(user("seller").roles("STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/home").with(user("buyer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user("buyer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/staff").with(user("buyer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
