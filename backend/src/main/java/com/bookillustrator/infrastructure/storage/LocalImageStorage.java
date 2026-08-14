package com.bookillustrator.infrastructure.storage;

import com.bookillustrator.application.port.output.ImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalImageStorage implements ImageStorage {

    private static final Map<String, String> EXTENSIONS_BY_MIME_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg");

    private final Path imagesDir;

    public LocalImageStorage(@Value("${storage.root}") String storageRoot) {
        this.imagesDir = Path.of(storageRoot, "images");
        try {
            Files.createDirectories(imagesDir);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create image storage directory: " + imagesDir, e);
        }
    }

    @Override
    public String save(byte[] imageBytes, String mimeType) {
        String extension = EXTENSIONS_BY_MIME_TYPE.getOrDefault(mimeType, "bin");
        Path path = imagesDir.resolve(UUID.randomUUID() + "." + extension);
        try {
            Files.write(path, imageBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write image to " + path, e);
        }
        return path.toString();
    }
}
