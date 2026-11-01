package com.tickethub.eventshow.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    @Column(name = "poster_ref", length = 500)
    private String posterRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public Event(String title, String description, EventType type, String posterRef, Instant now) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.posterRef = posterRef;
        this.status = EventStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String description, EventType type, String posterRef, Instant now) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.posterRef = posterRef;
        this.updatedAt = now;
    }

    /** Points the event at an uploaded poster (object key in the private bucket). */
    public void changePoster(String posterRef, Instant now) {
        this.posterRef = posterRef;
        this.updatedAt = now;
    }

    public void changeStatus(EventStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public EventType getType() { return type; }
    public EventStatus getStatus() { return status; }
    public String getPosterRef() { return posterRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
