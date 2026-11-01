package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.media.StoredObject;
import com.tickethub.eventshow.service.PosterService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Event posters (ST-30). Upload = ADMIN (POST under /events/** is an admin operation); download = any logged-in user.
 */
@RestController
@RequestMapping("/events/{eventId}/poster")
public class PosterController {

    private final PosterService posterService;

    public PosterController(PosterService posterService) {
        this.posterService = posterService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse upload(@PathVariable Long eventId, @RequestPart("file") MultipartFile file) {
        try {
            return posterService.upload(eventId, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The uploaded file could not be read.");
        }
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable Long eventId) {
        StoredObject poster = posterService.download(eventId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(poster.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(poster.bytes());
    }
}
