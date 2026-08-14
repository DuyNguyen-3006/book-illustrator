package com.bookillustrator.application.port.output;

/**
 * Book text lives on the local filesystem, not in the DB (spec §5.2). This port hides
 * where/how — the caller only gets back a path to persist alongside the project row.
 */
public interface BookTextStorage {

    String save(String bookText);

    String read(String path);

    /** Best-effort cleanup — e.g. when the DB write that would reference this path fails. */
    void delete(String path);
}
