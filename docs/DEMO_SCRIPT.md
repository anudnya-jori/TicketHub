# Demo script and explanations (about 15 minutes)

## 0. Say this first (30 s)
"TicketHub is event/movie ticket booking built as 7 Spring Boot microservices. Each service owns its own database. Customers
only talk to the API Gateway. Services find each other through Eureka and read settings from the Config Server. Booking
publishes a Kafka event when a booking is confirmed, and Notification turns it into an email."

## 1. Prepare (before the session)
`docker compose up -d` -> `mvn clean verify` -> start services in the order from GUIDE.md -> open http://localhost:8761 (all services
registered) and http://localhost:8025 (Mailpit inbox). Start notification-service with `NOTIFICATION_SENDER=smtp`.
Run `docker compose down -v && docker compose up -d` before the live demo so ids start at 1.

## 2. Walkthrough: what to run, what to say
| Step | Show | Say |
|---|---|---|
| Platform | Eureka page; `GET http://localhost:8888/booking-service/default` | Eureka = phone book (no hard-coded host:port; `lb://` and `http://booking-service`). Config Server = one place for settings; passwords are NOT in it (env vars). |
| Gateway | `curl localhost:8080/nothing` -> JSON 404; `curl localhost:8080/internal/bookings/1` -> 404 | One public door, same JSON error shape everywhere. `/internal/**` is deliberately not routed. |
| Epic 2 catalogue | `scripts/demo.sh` SETUP part; publish/open rules | Event DRAFT->PUBLISHED, Show SCHEDULED->OPEN. Only PUBLISHED events are listed; you cannot open a show of an unpublished event (409 `EVENT_NOT_PUBLISHED`). |
| Epic 3 booking | book seats 1,2 -> 201 PENDING_PAYMENT, amount 500 | Amount = price x seats, snapshotted at booking time. Seats are held for 10 min. |
| No double booking | Bob books seat 2 -> 409 `SEAT_UNAVAILABLE` | Enforced by the DATABASE: partial unique index on (show_id, seat_id) where status in (RESERVED, BOOKED). Show `V1__init_booking.sql`. App-level checks alone are racy; the index is the guarantee. Test: 8 threads, exactly 1 wins. |
| Idempotency | repeat same POST with same `Idempotency-Key` -> 200, same booking | Network retries must not create two bookings. Same key + different body -> 409 `IDEMPOTENCY_KEY_REUSED`. |
| Epic 4 payment | pay -> 201 SUCCESS, booking CONFIRMED; pay again -> 200 same payment | Amount comes from Booking, never from the client. `UNIQUE(booking_id)` prevents a double charge even in a race. |
| Failure | `X-Simulate-Outcome: FAILED` -> booking PAYMENT_FAILED, seats freed | Failure is a normal business outcome, handled. |
| Timeout | `X-Simulate-Delay-Ms: 6000` -> 504, payment PENDING | Provider didn't answer in 3 s. Outcome UNKNOWN, so we do NOT mark failed and do NOT charge again; we wait for the webhook. |
| Webhook | signed webhook -> APPLIED; replay -> DUPLICATE; bad signature -> 401 | HMAC-SHA256 over the raw body proves it came from the provider; event id is a primary key so replays are ignored. |
| Expiry | start booking-service with profile `demo` (1 min) or show the integration test | Unpaid booking -> EXPIRED, seats released; a late payment gets 409 `BOOKING_EXPIRED`. |
| Epic 5 email | Mailpit inbox shows the confirmation | Kafka decouples: if Notification is down the booking is still CONFIRMED; the message waits in Kafka. Duplicate delivery -> one email (`UNIQUE(event_id)`). |

## 3. Code tour (where to point)
- Controller -> Service -> Repository layering: `booking-service/.../controller`, `service/BookingService.java`, `repository`.
- Business rules in services, not controllers. DTOs (records) are the API; entities never leave the service.
- State machines: `BookingStatus`, `PaymentStatus` (illegal transitions throw 409 `INVALID_STATE_TRANSITION`).
- Error contract: `GlobalExceptionHandler` + `ErrorResponse` (identical in every service).
- Tests: `BookingFlowIntegrationTest` (real PostgreSQL via Testcontainers), `PaymentFlowIntegrationTest`, `NotificationFlowIntegrationTest`.

## 4. Likely viva questions
- *Why a database per service?* Independent deployment/scaling, no hidden coupling; cost: no joins/foreign keys across services, so we pass ids and call APIs.
- *Why sync REST for payment but Kafka for email?* Payment needs the answer now; email must never block or undo a booking.
- *What if Kafka is down when a booking confirms?* Publish failure is logged (`EVENT_PUBLISH_FAILED`), booking stays CONFIRMED. Phase 2 fixes this properly with the outbox pattern.
- *What if Booking is down when payment finishes?* `booking_synced=false`; a scheduler retries every 15 s.
- *Why not hold a DB transaction while calling the provider?* A slow provider would hold connections/locks.
- *Why Idempotency-Key?* Clients and networks retry; the server must make retries safe.
- *Security status?* Identity is the `X-Customer-Id` header in this build (a stand-in). Planned: Keycloak JWT at the gateway, roles CUSTOMER/ADMIN, ownership checks already exist in the services.

## 5. Phase 1 coverage - be upfront about it
Done and demonstrable: S0 repo/CI, S1 platform (Eureka/Config/Gateway), S2 catalogue, S3 booking, S4 payment, S5 notification/Kafka, error contract,
OpenAPI, Docker, release/rollback/smoke scripts, docs.
Designed but NOT implemented in this build: Keycloak/JWT security, Resilience4j circuit breaker/retry, Prometheus/Grafana/Jaeger/ELK,
S3 poster upload, real provider sandbox. If asked: "Timeouts and controlled 503/504 are in; circuit breakers and auth are the next sprint."
