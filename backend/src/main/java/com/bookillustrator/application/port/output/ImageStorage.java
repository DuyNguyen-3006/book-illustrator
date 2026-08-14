package com.bookillustrator.application.port.output;

import java.util.Optional;

/** Generated images live on the local filesystem, not in the DB (spec §5.2). */
public interface ImageStorage {

    /** @param mimeType e.g. "image/png" — determines the saved file's extension. */
    String save(byte[] imageBytes, String mimeType);

    /** Empty when the file is gone — the caller answers 404 rather than failing the request. */
    Optional<StoredImage> read(String path);

    record StoredImage(byte[] bytes, String mimeType) {
    }
}
