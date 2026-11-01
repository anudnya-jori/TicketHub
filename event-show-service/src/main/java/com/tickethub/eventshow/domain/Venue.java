package com.tickethub.eventshow.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String location;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Venue() {
    }

    public Venue(String name, String location, Instant now) {
        this.name = name;
        this.location = location;
        this.active = true;
        this.createdAt = now;
    }

    public void update(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
