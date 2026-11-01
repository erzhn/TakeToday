package com.example.taketoday;

import com.example.taketoday.notification.ConsoleMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Профиль prod (п. 5.2.1 ТЗ): сайт отвечает по https, cookie сессии только по https,
 * письма уходят через SMTP, а не в консоль.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "app.admin-email=",
        "app.admin-password=",
        "app.upload-dir=target/test-uploads",
        "app.reservations.expiry-enabled=false",
        "app.notifications.enabled=false"
})
@ActiveProfiles("prod")
class ProdProfileTests {

    private static final String PASSWORD = "test-keystore-password";

    /** Самоподписанный сертификат — как в README, через keytool из JDK. */
    @DynamicPropertySource
    static void keystore(DynamicPropertyRegistry registry) throws Exception {
        Path keystore = Files.createTempDirectory("taketoday-ssl").resolve("taketoday.p12");
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "taketoday", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", PASSWORD,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "1")
                .redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes());
        assertEquals(0, keytool.waitFor(), "keytool: " + output);
        registry.add("SSL_KEY_STORE", () -> "file:" + keystore);
        registry.add("SSL_KEY_STORE_PASSWORD", () -> PASSWORD);
    }

    @LocalServerPort int port;
    @Autowired ServerProperties server;
    @Autowired ApplicationContext context;

    @Test
    void servesSiteOverHttps() throws Exception {
        assertTrue(server.getSsl().isEnabled());
        assertTrue(server.getServlet().getSession().getCookie().getSecure());

        HttpClient client = HttpClient.newBuilder().sslContext(trustAll()).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("TakeToday"));
    }

    @Test
    void sendsRealMailInsteadOfConsole() {
        assertTrue(context.getBeansOfType(ConsoleMailSender.class).isEmpty());
        assertInstanceOf(JavaMailSenderImpl.class, context.getBean(JavaMailSender.class));
    }

    /** Сертификат самоподписанный — в тесте доверяем ему без проверки цепочки. */
    private static SSLContext trustAll() throws Exception {
        TrustManager trustAll = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{trustAll}, new SecureRandom());
        return ssl;
    }
}
