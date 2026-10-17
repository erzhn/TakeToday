package com.example.taketoday.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Сохраняет загруженные картинки (логотипы заведений, позже — фото предложений) в папку app.upload-dir.
 * Файл получает случайное имя, поэтому пользователь не может подменить чужой файл или задать опасное имя.
 * Отдаются файлы по адресу /uploads/{путь} — см. WebConfig.
 */
@Service
public class FileStorageService {

    /** Разрешены только JPG и PNG (SVG не принимаем — внутри него может быть скрипт). */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /** Проверяет файл. Возвращает текст ошибки или null, если всё хорошо. */
    public String validateImage(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.containsKey(type)) {
            return "Только JPG или PNG";
        }
        if (file.getSize() > maxBytes) {
            return "Файл больше " + (maxBytes / (1024 * 1024)) + " МБ";
        }
        return null;
    }

    /**
     * Сохраняет картинку в подпапку (например «venues») и возвращает путь вида «venues/uuid.png».
     * Перед вызовом файл нужно проверить через validateImage.
     */
    public String saveImage(MultipartFile file, String folder) {
        String extension = ALLOWED_TYPES.get(file.getContentType().toLowerCase(Locale.ROOT));
        String relative = folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить файл", e);
        }
        return relative;
    }

    /** Удаляет ранее сохранённый файл. Если файла нет — ничего не делает. */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            return; // защита от путей вида ../../
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // старый файл не удалился — не страшно, на работу сайта это не влияет
        }
    }
}
