package com.bookillustrator.application.port.output;

/** Generated images live on the local filesystem, not in the DB (spec §5.2). */
public interface ImageStorage {

    /** @param mimeType e.g. "image/png" — determines the saved file's extension. */
    String save(byte[] imageBytes, String mimeType);
}
