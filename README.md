# TicketHub - Phase 1 + Phase 2

**Phase 2 status:** all scenarios 0-14 have code, but 14 is scripts + notes only (no recorded runs). Nothing in Phase 2 has been compiled or run yet: first run `mvn clean verify` and fix anything it reports.
Read `docs/PHASE2.md` (checklist, assumptions, incident notes) and `docs/KAFKA_INSPECTION.md`.

Quick start: `cp .env.example .env` -> `docker compose up -d` (adds Kafka UI on :8090) -> `mvn clean verify` ->
run services per `GUIDE.md` -> `bash scripts/demo.sh`. Databases must be fresh (`docker compose down -v`) because
Flyway V2 migrations were added to booking, payment and notification.

---
# TicketHub (Phase 1)

Event/movie ticket booking as microservices: Eureka, Config Server, API Gateway, Event/Show, Booking, Payment, Notification.
Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0 · PostgreSQL 16 · Kafka · Maven.

Start with **GUIDE.md** (run it) and **docs/ARCHITECTURE.md** (understand it).
