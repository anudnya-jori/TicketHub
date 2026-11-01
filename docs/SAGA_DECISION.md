# Scenario 7 - choreography vs orchestration (decision note)
TicketHub keeps **choreography** (Payment and Booking react to each other's events) and adds a **read-only saga view** in Booking:
`GET /internal/bookings/{id}/saga` returns state (PAYMENT_PENDING, CONFIRMED, FAILED, COMPENSATING, COMPENSATED), a plain-language
"why", and the ordered events received (inbox) and sent (outbox, QUEUED vs PUBLISHED). Answers "where is booking X and why?".

| | Choreography (kept) | Orchestration |
|---|---|---|
| Visibility | needs the saga view above | one place knows the flow |
| Coupling | services only know events | coordinator knows every service |
| Complexity | low for 3 steps | extra component + its own state/reliability |
| Failure handling | each service compensates locally | coordinator decides, but is a single point to keep reliable |
Reason: only two services take part in the money flow and compensations are simple (refund). Revisit if more steps are added.
Not built: a prototype orchestrator (the guide allows "implement OR prototype"; the state view is the implemented half).

# Scenario 8/9 - reconciliation and payment resolution
`POST payment-service:8083/internal/reconciliation/run` (also every 60 s) returns a report. See `ReconciliationService`.
PENDING (timed-out) payments are resolved by fetching the status from the provider (`PaymentProvider.fetchStatus`; simulated here).
Webhooks: duplicate event ids are dropped (Phase 1), a final payment is never changed by a later webhook, states only move forward.

# Scenario 12 - replay runbook
1. Retention: `booking.confirmed` keeps 7 days (`retention.ms=604800000`, set in `KafkaErrorHandlingConfig`). Older events cannot be replayed.
2. Start a NEW group in replay mode: `NOTIFICATION_GROUP_ID=replay-1 NOTIFICATION_REPLAY_MODE=true` (reads from earliest, writes records as REPLAYED, sends nothing).
3. Verify: `select status,count(*) from notifications group by 1;` and Mailpit shows no new mail.
4. Stop the replay instance, remove the group (`kafka-consumer-groups.sh --delete --group replay-1`).
5. Never run the normal group in replay mode, and never replay with replay-mode=false against live recipients.

# Scenario 13 - contract versioning
BookingConfirmed is now `version: 2`: adds `totalAmount` and keeps `amount` (same value) so v1 consumers still work. Notification reads
`totalAmount`, falling back to `amount`, and ignores unknown fields (test `version1AndVersion2PayloadsBothParse`).
Deviation from the guide: instead of publishing two separate event versions side by side, one event carries both field names during the migration;
drop `amount` once all consumers read `totalAmount`. A rename without this would break old consumers and old events still in the topic.
Schema Registry (Avro/Protobuf) is the industry way to enforce this automatically; it is not built.
