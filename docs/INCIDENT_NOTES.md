# Incident notes (reproduce with `scripts/chaos-lab.sh <name>`; fill the "observed" column when you run it)
| Failure | Symptom | Cause | Recovery / fix | Prevention | Observed |
|---|---|---|---|---|---|
| booking-down | Paid, booking pending | Booking offline when payment finished | Event waits in Kafka; Booking confirms on restart (one email, inbox) | Outbox + inbox | not yet run |
| kafka-down | Confirmed/paid, no event | Kafka offline | Rows wait in outbox (`published_at` null); relay sends after restart | Alert on unpublished outbox rows | not yet run |
| webhooks | Duplicate/reordered callbacks | Provider retries | Event-id dedupe, forward-only states | Keep handler idempotent | not yet run |
| expired-payment | Paid for expired booking | Late payment | Booking REJECTED -> BookingRejected -> refund; reconciliation catches leftovers | Reconciliation job | not yet run |
| mail-down | Emails delayed | SMTP down | RETRYING, retry topics, breaker; sent once after recovery | Breaker + rate limit | not yet run |
| poison | Lag stuck | Bad message | DLT, `/internal/dead-letters`, replay after fix | DLT-depth alert | not yet run |
| replay | History needed | Restored DB | New group + replay mode, no emails | Runbook in SAGA_DECISION.md | not yet run |
