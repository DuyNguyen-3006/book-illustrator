package com.bookillustrator.infrastructure.storage;

import com.bookillustrator.application.port.output.BookTextStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalFileStorage implements BookTextStorage {

    private final Path booksDir;

    public LocalFileStorage(@Value("${storage.root}") String storageRoot) {
        this.booksDir = Path.of(storageRoot, "books");
        try {
            Files.createDirectories(booksDir);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create book storage directory: " + booksDir, e);
        }
    }

    @Override
    public String save(String bookText) {
        Path path = booksDir.resolve(UUID.randomUUID() + ".txt");
        try {
            Files.writeString(path, bookText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write book text to " + path, e);
        }
        return path.toString();
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ignored) {
            // Best-effort — the original failure is what the caller reports.
        }
    }
}
