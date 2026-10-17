package com.example.taketoday.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Отдаёт загруженные файлы (логотипы, фото) по адресу /uploads/**.
 * Папку берём из настройки напрямую, без FileStorageService, — так класс не мешает
 * тестам @WebMvcTest, где сервисы не создаются.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Путь к папке обязательно должен заканчиваться на «/», иначе Spring не найдёт файлы внутри
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
