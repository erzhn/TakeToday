package kg.taketoday;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что приложение запускается и разделы открываются по ролям (п. 5.2.3 ТЗ).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessByRoleTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homePageIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void guestIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void buyerCannotOpenStaffOrAdmin() throws Exception {
        mvc.perform(get("/staff")).andExpect(status().isForbidden());
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotOpenAdmin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }
}
