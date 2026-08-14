package com.bookillustrator.infrastructure.storage;

import com.bookillustrator.application.port.output.ImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LocalImageStorage implements ImageStorage {

    private static final Map<String, String> EXTENSIONS_BY_MIME_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg");

    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg");

    private final Path imagesDir;

    public LocalImageStorage(@Value("${storage.root}") String storageRoot) {
        this.imagesDir = Path.of(storageRoot, "images").toAbsolutePath().normalize();
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

    @Override
    public Optional<StoredImage> read(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        Path file;
        try {
            file = Path.of(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        // These paths come from our own DB rows, but this method turns a stored string
        // into a file read on request, so it stays confined to the images directory
        // regardless of what ends up in that column.
        if (!file.startsWith(imagesDir) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new StoredImage(Files.readAllBytes(file), mimeTypeOf(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read image from " + file, e);
        }
    }

    private static String mimeTypeOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        return MIME_TYPES_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
    }
}
