# TicketHub Phase 2 - what was built, how to run it, honest status

## Event flow (all events carry eventId, eventType, version, occurredAt, bookingId = Kafka key, aggregateVersion)
```
Payment --outbox--> payment.events  : PaymentSucceeded | PaymentFailed | PaymentRefunded  --> Booking (inbox)
Booking --outbox--> booking.events  : BookingRejected | BookingCancelled                  --> Payment (inbox)
Booking --outbox--> booking.confirmed : BookingConfirmed                                  --> Notification (inbox)
Dead letters: <topic>.DLT  (booking.confirmed.DLT, payment.events.DLT, booking.events.DLT)
```
Booking states: PENDING_PAYMENT -> CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED | REJECTED;
CONFIRMED -> CANCELLED (refund); EXPIRED/CANCELLED/PAYMENT_FAILED -> REJECTED (late payment, refund).
`refundStatus`: NONE -> REQUESTED -> REFUNDED. Payment states add SUCCESS -> REFUNDED.

## Checklist (status is about CODE WRITTEN; nothing could be compiled or run where this was produced - see below)
| # | Scenario | Tier | Status |
|---|---|---|---|
| 0 | Kafka UI in Compose (`http://localhost:8090`), inspection guide | Core | DONE - `docker-compose.yml`, `docs/KAFKA_INSPECTION.md` |
| 1 | Retry + backoff + DLT, non-retryable bad data, DLT inspect/replay | Core | DONE (Notification consumer; same handler in Booking/Payment) |
| 2 | Inbox / idempotent consumer | Core | DONE (Notification, Booking, Payment) |
| 3 | Transactional outbox + relay | Core | DONE (Booking, Payment) |
| 4 | Payment->Booking saga by events, REST callback removed | Core | DONE |
| 5 | Compensation: BookingRejected/Cancelled -> refund -> PaymentRefunded | Core | DONE (refund against the SIMULATED provider) |
| 6 | Key = bookingId, aggregateVersion, ignore stale | Core | DONE |
| 7 | Orchestration comparison | Important | DONE (choreography kept + saga view endpoint + decision note; no orchestrator prototype) - `docs/SAGA_DECISION.md` |
| 8 | Reconciliation job | Important | DONE - `ReconciliationService`, `POST /internal/reconciliation/run` (starts from payments; see limitation in code) |
| 9 | Webhook idempotency/forward-only/UNKNOWN state | Important | DONE - forward-only states, event-id dedupe, PENDING resolved by provider fetch (simulated provider) |
| 10 | Email outage: retry topics, breaker, rate limit | Important | DONE - retry topics `-retry`, RETRYING status, circuit breaker + rate limit, DLT |
| 11 | Consumer lag / scaling demo | Important | DONE (scripts/config, not run) - `scripts/lag-demo.sh`, `NOTIFICATION_DELAY_MS`, kafka-exporter + Grafana lag panel (threshold line 100) |
| 12 | Replay mode | Stretch | DONE - retention set, new group id + replay mode, runbook |
| 13 | Contract versioning | Stretch | DONE with deviation - tolerant dual-field v2 instead of two parallel versions; parse test added |
| 14 | Everything breaks together lab | Final | PARTLY - `scripts/chaos-lab.sh` reproduces each failure; `docs/INCIDENT_NOTES.md` has notes but "observed" is empty because nothing was run |

Still open: running the labs and recording real observations; an orchestrator prototype; Schema Registry; a real Razorpay provider.

## Decisions / assumptions
- Topic names follow the guide: `booking.confirmed` (renamed from `booking-confirmed`; config, tests and config-repo updated).
- The payment provider is still simulated; `refund()` returns a deterministic id `rfnd_<providerReference>`, which makes refunds idempotent. A real Razorpay Refunds call is another `PaymentProvider` implementation (not written).
- Duplicate whose first attempt never finished sending: a PENDING/FAILED notification is resumed (sent), a SENT one is skipped.
- Refund and inbox insert share one DB transaction (bounded provider timeout) so a failed refund is retried instead of lost.
- Outbox relay stops at the first failed row to preserve order; several relay instances can send duplicates, which inboxes absorb.
- `/internal/bookings/{id}/payment-result` and `BookingClient.reportResult` were removed (Scenario 4). Booking still exposes GET `/internal/bookings/{id}` for Payment's pre-charge read.

## Incident notes (Core scenarios)
| Scenario | Symptom | Cause | Fix | Prevention |
|---|---|---|---|---|
| 1 | Lag grows, no emails | Poison message retried forever | Bounded retries, non-retryable bad data, `*.DLT`, replay endpoint | Alert on DLT depth |
| 2 | Duplicate emails | At-least-once delivery | Inbox table with PK on event id, same tx as effect | Keep every consumer idempotent |
| 3 | Confirmed booking, no email | Kafka down between commit and publish | Outbox row in same tx + relay | Alert on unpublished outbox rows |
| 4 | Paid, booking stays pending | REST callback failed | Events; Kafka holds them until Booking is back | - |
| 5 | Paid for dead booking / cancelled but charged | No compensation | BookingRejected/Cancelled -> refund | Reconciliation (scenario 8, not built) |
| 6 | Wrong final state | Reordered events | Key = bookingId, aggregateVersion guard | Always key by aggregate id |

## Tests
New/updated: `NotificationServiceTest`, `KafkaErrorHandlingConfigTest`, `OutboxRelayTest` (booking, payment) - no Docker needed;
`NotificationFlowIntegrationTest`, `BookingFlowIntegrationTest`, `PaymentFlowIntegrationTest`, `BookingStatusTest`, `PaymentStatusTest` (Testcontainers/Docker for the integration ones).

## What was and was not verified (read before submitting)
- All 244 Java files parse with no syntax errors (tree-sitter-java), and the real `javac` was run over all modules WITHOUT the Spring/Kafka
  jars (not downloadable here). It reported no unresolved project classes/methods and no constructor/argument mismatches in project code;
  every remaining error is a missing third-party library, which is expected without Maven.
- NOT verified: dependency resolution, Spring wiring, Flyway migrations on PostgreSQL, Hibernate schema validation, every test, Docker images,
  Kafka behaviour (retry topics, DLT, relay), and all scripts' runtime behaviour. Run `mvn clean verify`, then `docker compose up -d` + `scripts/demo.sh`.
