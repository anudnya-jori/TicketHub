package com.tickethub.eventshow.service;

import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.media.MediaStorage;
import com.tickethub.eventshow.media.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Event poster upload/download (ST-30).
 * Rules: only JPEG/PNG/WebP, at most media.max-poster-bytes, and the file's REAL content (magic bytes) must match the
 * declared type (a .exe renamed to .png is rejected). The event stores only the object key; the bucket is private,
 * so downloads go through this service (which applies the normal login/role rules).
 */
@Service
public class PosterService {

    private static final Logger log = LoggerFactory.getLogger(PosterService.class);

    /** declared content type -> file extension */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final EventService eventService;
    private final MediaStorage storage;
    private final long maxBytes;

    public PosterService(EventService eventService, MediaStorage storage,
                         @Value("${media.max-poster-bytes:2097152}") long maxBytes) {
        this.eventService = eventService;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public EventResponse upload(Long eventId, byte[] bytes, String declaredContentType) {
        EventResponse event = eventService.getEvent(eventId);          // 404 EVENT_NOT_FOUND early
        if (event.status().name().equals("CANCELLED")) {
            throw ApiException.conflict("EVENT_CANCELLED", "A cancelled event cannot get a new poster.");
        }
        validate(bytes, declaredContentType);

        String key = "posters/event-" + eventId + "/" + UUID.randomUUID() + "." + ALLOWED.get(declaredContentType);
        storage.put(key, bytes, declaredContentType);                    // 1. store the file
        String oldKey = event.posterRef();
        EventResponse updated = eventService.attachPoster(eventId, key); // 2. then record the reference
        log.info("POSTER_UPLOADED eventId={} key={} bytes={}", eventId, key, bytes.length);
        if (oldKey != null && oldKey.startsWith(keyPrefix(eventId)) && !oldKey.equals(key)) {
            storage.delete(oldKey);                                      // 3. housekeeping
        }
        return updated;
    }

    public StoredObject download(Long eventId) {
        EventResponse event = eventService.getEvent(eventId);
        String key = event.posterRef();
        // only keys we created for THIS event are served (an admin-supplied posterRef can never read other objects)
        if (key == null || !key.startsWith(keyPrefix(eventId))) {
            throw ApiException.notFound("POSTER_NOT_FOUND", "Event " + eventId + " has no uploaded poster.");
        }
        StoredObject object = storage.get(key)
                .orElseThrow(() -> ApiException.notFound("POSTER_NOT_FOUND", "Poster file not found."));
        String ext = key.substring(key.lastIndexOf('.') + 1);
        String type = ALLOWED.entrySet().stream().filter(e -> e.getValue().equals(ext)).map(Map.Entry::getKey)
                .findFirst().orElse("application/octet-stream");
        return new StoredObject(object.bytes(), type);
    }

    private void validate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "The uploaded file is empty.");
        }
        if (bytes.length > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The poster must be at most " + (maxBytes / 1024) + " KB.");
        }
        if (declaredContentType == null || !ALLOWED.containsKey(declaredContentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                    "Only JPEG, PNG or WebP images are allowed.");
        }
        if (!declaredContentType.equals(sniff(bytes))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_CONTENT_MISMATCH",
                    "The file content does not match the declared image type.");
        }
    }

    /** Identifies the type from the first bytes, ignoring what the client claimed. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return "unknown";
    }

    private static String keyPrefix(Long eventId) {
        return "posters/event-" + eventId + "/";
    }
}
