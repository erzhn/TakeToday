package com.example.taketoday;

import com.example.taketoday.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mail.SimpleMailMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","app.admin-email=","app.admin-password="})
@Import(AuthIntegrationTests.MailConfig.class)
@AutoConfigureMockMvc
class PasswordRecoveryTests {
    @Autowired PasswordRecoveryService service;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean AuthIntegrationTests.CapturingMail mail;
    @Autowired MockMvc mvc;
    UUID account;
    @BeforeEach void setup(){
        db.update("DELETE FROM password_recovery");
        db.update("DELETE FROM accounts");
        mail.fail=false; account=UUID.randomUUID();
        db.update("INSERT INTO accounts(id,email,password_hash,name,role) VALUES (?,?,?,?,?)",account,"user@gmail.com",encoder.encode("OldPassword123"),"User","CUSTOMER");
    }
    @Test void requiresVerifiedCodeAndRejectsReplay(){
        UUID id=service.request("USER@gmail.com").challengeId();
        assertThrows(ApiError.class,()->service.reset(id,UUID.randomUUID(),"NewPassword123"));
        String wrong=mail.code.equals("000000")?"000001":"000000";
        assertThrows(ApiError.class,()->service.verify(id,wrong));
        UUID token=service.verify(id,mail.code);
        assertThrows(ApiError.class,()->service.reset(id,UUID.randomUUID(),"NewPassword123"));
        service.reset(id,token,"NewPassword123");
        assertTrue(encoder.matches("NewPassword123",db.queryForObject("SELECT password_hash FROM accounts WHERE id=?",String.class,account)));
        assertEquals(1,db.queryForObject("SELECT auth_version FROM accounts WHERE id=?",Integer.class,account));
        assertThrows(ApiError.class,()->service.reset(id,token,"AnotherPassword123"));
    }
    @Test void locksAfterFiveWrongCodes(){
        UUID id=service.request("user@gmail.com").challengeId();
        String correct=mail.code,wrong=correct.equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++)assertThrows(ApiError.class,()->service.verify(id,wrong));
        assertThrows(ApiError.class,()->service.verify(id,correct));
    }
    @Test void expiresAndResendsIndependently(){
        UUID old=service.request("user@gmail.com").challengeId();
        String oldCode=mail.code;
        assertThrows(ApiError.class,()->service.request("user@gmail.com"));
        db.update("UPDATE password_recovery SET sent_at=?",Timestamp.from(Instant.now().minusSeconds(61)));
        UUID fresh=service.request("user@gmail.com").challengeId();
        assertThrows(ApiError.class,()->service.verify(old,oldCode));
        db.update("UPDATE password_recovery SET expires_at=?",Timestamp.from(Instant.now().minusSeconds(1)));
        assertThrows(ApiError.class,()->service.verify(fresh,mail.code));
    }
    @Test void mailFailureRollsBackAndUnknownAccountDoesNotPretendToSend(){
        assertThrows(ApiError.class,()->service.request("unknown@gmail.com"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM password_recovery",Integer.class));
        mail.fail=true;
        assertThrows(org.springframework.mail.MailException.class,()->service.request("user@gmail.com"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM password_recovery",Integer.class));
    }

    @Test void browserRequestSendsCodeToRegisteredAddress() throws Exception {
        org.mockito.Mockito.clearInvocations(mail);
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/recovery/request")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"email\":\"USER@gmail.com\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"user@gmail.com"},message.getValue().getTo());
        assertTrue(message.getValue().getText().contains(mail.code));
        assertTrue(response.getResponse().getContentAsString().contains("challengeId"));
    }

    @Test void unknownAddressReturnsErrorInsteadOfCodeScreen() throws Exception {
        org.mockito.Mockito.clearInvocations(mail);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/recovery/request")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"email\":\"unknown@gmail.com\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
