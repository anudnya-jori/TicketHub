# Evidence checklist (fill while you demonstrate; attach screenshots/outputs)

| Story | What to show | Evidence |
|---|---|---|
| ST-61 | `docker compose up -d`, `docker compose ps`, `docker compose down` | screenshot |
| ST-03/04/48/49 | demo.sh catalogue section PASS lines | output |
| ST-05 | `GET /shows/{id}/seats` with RESERVED/BOOKED | output |
| ST-06/07/08 | booking, 409 SEAT_UNAVAILABLE, `BookingFlowIntegrationTest` (8 threads, 1 winner) | test report |
| ST-09 | `expireOverdueBookings` test; late payment → BOOKING_EXPIRED | test report |
| ST-53 | same Idempotency-Key → same booking | output |
| ST-20..24/54/55 | payment success/fail/duplicate/webhook | output, test report |
| ST-19/37 | Kafka event + one email in Mailpit; duplicate delivery test | screenshot |
| ST-10..13 | Eureka page, Config Server `/booking-service/default`, gateway 404 JSON | screenshots |
| ST-15 | Keycloak realm, tokens for alice/bob/admin | screenshot |
| ST-14 | 401 without token (demo.sh) | output |
| ST-59 | 403 customer creating event; 404 on other's booking | output |
| ST-16 | provider timeout → 504 + PENDING | output |
| ST-17 | `ResilientCallerTest` (reads retried, payment charge never retried) | test report |
| ST-18 | `resilience-demo.sh` open → fail fast → recovery; Grafana breaker panel | output, screenshot |
| ST-25 | JSON logs with bookingId/paymentId/correlationId; no tokens/secrets | log excerpt |
| ST-26 | Kibana search by bookingId | screenshot |
| ST-27 | Grafana dashboard under traffic | screenshot |
| ST-28 | Jaeger trace gateway → booking → event-show | screenshot |
| ST-29 | filled incident record (`docs/INCIDENT_LAB.md`) | document |
| ST-30 | poster upload/download, fake file rejected, bucket private | output |
| ST-31 | RDS/secrets procedure + `git grep` showing no credentials | document |
| ST-32/33/34 | images built in CI, deployment record, release + rollback + smoke outputs | CI link, outputs |
| ST-62 | full `demo.sh` run, `N passed, 0 failed` | output |
