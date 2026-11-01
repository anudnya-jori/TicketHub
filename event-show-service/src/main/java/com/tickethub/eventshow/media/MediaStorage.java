package com.tickethub.eventshow.media;

import java.util.Optional;

/** Where uploaded media lives. Today S3; tests use an in-memory fake. Throws ApiException(503) if storage is down. */
public interface MediaStorage {
    void put(String key, byte[] bytes, String contentType);

    Optional<StoredObject> get(String key);

    void delete(String key);
}
