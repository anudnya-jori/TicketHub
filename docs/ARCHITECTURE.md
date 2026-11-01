# TicketHub — Architecture (HLD, LLD, UML)

## 1. High-level design
```mermaid
flowchart LR
  C[Client / curl] --> GW[API Gateway :8080]
  GW --> ES[Event/Show :8081]
  GW --> BK[Booking :8082]
  GW --> PY[Payment :8083]
  BK -- "GET show, seat layout" --> ES
  ES -- "GET seat states" --> BK
  PY -- "GET booking / POST payment-result" --> BK
  BK -- "booking-confirmed" --> K[(Kafka)]
  K --> NT[Notification :8084]
  NT --> M[Mailpit / SMTP]
  ES --- D1[(eventshow_db)]
  BK --- D2[(booking_db)]
  PY --- D3[(payment_db)]
  NT --- D4[(notification_db)]
  EU[Eureka :8761] -.registry.- GW
  CF[Config Server :8888] -.config.- ES
```
Why this shape: **database per service** (no service reads another's tables), **synchronous REST** where the caller needs the
answer now (Booking needs the show; Payment needs the booking amount), **Kafka** where the caller must not wait or care
(confirmation email). The Gateway is the only public entry; `/internal/**` is not routed.

## 2. Services and responsibilities
| Service | Owns | Key endpoints |
|---|---|---|
| Event/Show | events, venues, seats, shows | `/events`, `/venues`, `/shows`, `/shows/{id}/seats`, `/internal/shows/{id}/seat-layout` |
| Booking | bookings, seat holds | `POST /bookings`, `GET /bookings/{id}`, `POST /bookings/{id}/cancel`, `/internal/...` |
| Payment | payments, webhook events | `POST /payments`, `GET /payments/{id}`, `POST /payments/webhook` |
| Notification | notifications | Kafka listener, `GET /internal/notifications?bookingId=` |

## 3. Entities (ER)
```mermaid
erDiagram
  EVENT ||--o{ SHOW : has
  VENUE ||--o{ SHOW : hosts
  VENUE ||--o{ SEAT : contains
  BOOKING ||--|{ BOOKING_SEAT : holds
  PAYMENT ||--o{ WEBHOOK_EVENT : receives
  EVENT { bigint id string title string type string status }
  SHOW { bigint id bigint event_id bigint venue_id timestamptz start_time numeric price string status }
  SEAT { bigint id bigint venue_id string row_label int seat_number string category }
  BOOKING { bigint id string customer_id bigint show_id numeric amount string status timestamptz expiry_at string idempotency_key }
  BOOKING_SEAT { bigint booking_id bigint show_id bigint seat_id string status }
  PAYMENT { bigint id bigint booking_id numeric amount string status string provider_reference boolean booking_synced }
  WEBHOOK_EVENT { string event_id bigint payment_id }
```
Cross-service references (booking.show_id, payment.booking_id) are plain ids, **not** foreign keys: that is what "database per service" means.

## 4. State machines
```mermaid
stateDiagram-v2
  [*] --> PENDING_PAYMENT
  PENDING_PAYMENT --> CONFIRMED: payment SUCCESS
  PENDING_PAYMENT --> PAYMENT_FAILED: payment FAILED
  PENDING_PAYMENT --> CANCELLED: customer cancels
  PENDING_PAYMENT --> EXPIRED: 10 min passed
```
```mermaid
stateDiagram-v2
  [*] --> INITIATED
  INITIATED --> SUCCESS
  INITIATED --> FAILED
  INITIATED --> PENDING: provider timeout
  PENDING --> SUCCESS: webhook
  PENDING --> FAILED: webhook
```
Event: DRAFT → PUBLISHED → CANCELLED. Show: SCHEDULED → OPEN → CLOSED/CANCELLED. Final states never change.

## 5. Main flow (sequence)
```mermaid
sequenceDiagram
  actor U as Customer
  participant G as Gateway
  participant B as Booking
  participant E as Event/Show
  participant P as Payment
  participant K as Kafka
  participant N as Notification
  U->>G: POST /bookings (Idempotency-Key)
  G->>B: forward
  B->>E: GET /shows/{id}, seat layout
  B->>B: insert seats (unique index) -> PENDING_PAYMENT
  B-->>U: 201 booking
  U->>G: POST /payments {bookingId}
  G->>P: forward
  P->>B: GET /internal/bookings/{id} (amount, owner, state)
  P->>P: provider charge (timeout 3s)
  P->>B: POST payment-result SUCCESS
  B->>B: CONFIRMED, seats BOOKED
  B-)K: booking-confirmed
  K-)N: event
  N->>N: dedupe, send email
  P-->>U: 201 payment SUCCESS
```

## 6. The hard problems and how they are solved
| Problem | Solution | Proof |
|---|---|---|
| Two customers, same seat | Partial unique index `(show_id, seat_id) WHERE status IN (RESERVED,BOOKED)`; loser gets 409 `SEAT_UNAVAILABLE` | SQL test + `BookingFlowIntegrationTest` (8 threads) |
| Client retries create booking | `Idempotency-Key` + request fingerprint: same → same booking (200); different body → 409 | integration test |
| Unpaid bookings hold seats forever | `expiry_at` + scheduler sets EXPIRED and releases seats; late payment → 409 `BOOKING_EXPIRED` | integration test |
| Double charge | `UNIQUE(booking_id)` on payments; race handled | `concurrentPayRequests...` test |
| Provider slow / unknown outcome | hard timeout → PENDING + 504; webhook resolves it | `providerTimeout...` test |
| Fake webhook | HMAC-SHA256 of the raw body, constant-time compare | webhook tests |
| Webhook replay / late conflict | `webhook_events` primary key; final payments never change | webhook tests |
| Booking down when payment ends | `booking_synced=false`, scheduler retries | `ifBookingIsDown...` test |
| Duplicate Kafka delivery | notification `UNIQUE(event_id)` | `...DeliveredTwice...` test |
| Email fails | notification FAILED, booking stays CONFIRMED | `aFailingEmail...` test |

## 7. Error contract
Every error: `{timestamp, status, error, code, message, path, details[]}`. Codes are stable (e.g. `SEAT_UNAVAILABLE`,
`BOOKING_EXPIRED`, `IDEMPOTENCY_KEY_REUSED`, `INVALID_SIGNATURE`); the gateway returns the same shape for 404/502/503/504.

## 8. Still to do (not in this zip)
Keycloak/JWT security, Resilience4j circuit breaker/retry, observability stack, S3 poster upload, outbox for guaranteed
event delivery (Phase 2), real provider sandbox. Today customer identity is the `X-Customer-Id` header and the booking API
trusts it — **do not expose this build to the internet**.
