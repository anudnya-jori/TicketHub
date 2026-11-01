package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.media.MediaStorage;
import com.tickethub.eventshow.media.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PosterServiceTest {

    /** In-memory stand-in for S3. */
    static class FakeStorage implements MediaStorage {
        final Map<String, StoredObject> objects = new HashMap<>();
        boolean down;

        public void put(String key, byte[] bytes, String contentType) {
            if (down) throw ApiException.unavailable("MEDIA_STORAGE_UNAVAILABLE", "down");
            objects.put(key, new StoredObject(bytes, contentType));
        }

        public Optional<StoredObject> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        public void delete(String key) {
            objects.remove(key);
        }
    }

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

    EventService eventService = mock(EventService.class);
    FakeStorage storage = new FakeStorage();
    PosterService service;
    String storedKey;

    @BeforeEach
    void setUp() {
        service = new PosterService(eventService, storage, 1024);
        when(eventService.getEvent(1L)).thenAnswer(i -> event(1L, EventStatus.DRAFT, storedKey));
        when(eventService.attachPoster(eq(1L), anyString())).thenAnswer(i -> {
            storedKey = i.getArgument(1);
            return event(1L, EventStatus.DRAFT, storedKey);
        });
    }

    private EventResponse event(Long id, EventStatus status, String poster) {
        return new EventResponse(id, "Dune", "x", EventType.MOVIE, status, poster, Instant.now());
    }

    private String code(Runnable r) {
        return assertThrows(ApiException.class, r::run).getCode();
    }

    @Test
    void validPngIsStoredUnderAnEventScopedKeyAndReferencedByTheEvent() {
        EventResponse result = service.upload(1L, PNG, "image/png");

        assertTrue(result.posterRef().startsWith("posters/event-1/"));
        assertTrue(result.posterRef().endsWith(".png"));
        assertEquals(1, storage.objects.size());
    }

    @Test
    void downloadReturnsTheBytesWithTheRightContentType() {
        service.upload(1L, JPEG, "image/jpeg");

        StoredObject poster = service.download(1L);

        assertEquals("image/jpeg", poster.contentType());
        assertArrayEquals(JPEG, poster.bytes());
    }

    @Test
    void replacingAPosterDeletesTheOldFile() {
        service.upload(1L, PNG, "image/png");
        String first = storedKey;
        service.upload(1L, JPEG, "image/jpeg");

        assertFalse(storage.objects.containsKey(first));
        assertEquals(1, storage.objects.size());
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedFiles() {
        assertEquals("EMPTY_FILE", code(() -> service.upload(1L, new byte[0], "image/png")));
        assertEquals("FILE_TOO_LARGE", code(() -> service.upload(1L, new byte[2000], "image/png")));
        assertEquals("UNSUPPORTED_MEDIA_TYPE", code(() -> service.upload(1L, PNG, "application/pdf")));
        assertEquals("UNSUPPORTED_MEDIA_TYPE", code(() -> service.upload(1L, PNG, null)));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void rejectsAFileWhoseContentDoesNotMatchItsDeclaredType() {
        byte[] script = "<script>alert(1)</script>".getBytes();

        assertEquals("FILE_CONTENT_MISMATCH", code(() -> service.upload(1L, script, "image/png")));
        assertEquals("FILE_CONTENT_MISMATCH", code(() -> service.upload(1L, JPEG, "image/png")));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void storageOutageGivesA503AndTheEventIsNotChanged() {
        storage.down = true;

        ApiException ex = assertThrows(ApiException.class, () -> service.upload(1L, PNG, "image/png"));

        assertEquals(503, ex.getStatus().value());
        verify(eventService, never()).attachPoster(any(), any());
    }

    @Test
    void cancelledEventsCannotGetAPoster() {
        when(eventService.getEvent(2L)).thenReturn(event(2L, EventStatus.CANCELLED, null));

        assertEquals("EVENT_CANCELLED", code(() -> service.upload(2L, PNG, "image/png")));
    }

    @Test
    void noPosterMeansNotFoundAndForeignKeysAreNeverServed() {
        assertEquals("POSTER_NOT_FOUND", code(() -> service.download(1L)));

        storedKey = "posters/event-99/secret.png";            // an admin-supplied ref pointing at another event's file
        storage.objects.put(storedKey, new StoredObject(PNG, "image/png"));
        assertEquals("POSTER_NOT_FOUND", code(() -> service.download(1L)));

        storedKey = "https://example.com/p.png";               // an external URL is not an uploaded poster
        assertEquals("POSTER_NOT_FOUND", code(() -> service.download(1L)));
    }
}
