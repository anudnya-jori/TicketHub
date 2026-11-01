package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.media.StoredObject;
import com.tickethub.eventshow.service.PosterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PosterController.class)
class PosterControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    PosterService posterService;

    @Test
    void uploadReturnsTheEventWithItsPosterReference() throws Exception {
        when(posterService.upload(eq(1L), any(), eq("image/png"))).thenReturn(new EventResponse(1L, "Dune", "x",
                EventType.MOVIE, EventStatus.DRAFT, "posters/event-1/a.png", Instant.now()));

        mockMvc.perform(multipart("/events/1/poster").file(new MockMultipartFile("file", "p.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterRef").value("posters/event-1/a.png"));
    }

    @Test
    void invalidFileIsReportedWithTheStandardErrorFormat() throws Exception {
        when(posterService.upload(any(), any(), any()))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", "Only JPEG, PNG or WebP images are allowed."));

        mockMvc.perform(multipart("/events/1/poster").file(new MockMultipartFile("file", "p.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void downloadStreamsTheImageWithItsContentType() throws Exception {
        when(posterService.download(1L)).thenReturn(new StoredObject(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/events/1/poster"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void missingPosterIs404() throws Exception {
        when(posterService.download(2L)).thenThrow(ApiException.notFound("POSTER_NOT_FOUND", "none"));

        mockMvc.perform(get("/events/2/poster")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTER_NOT_FOUND"));
    }
}
