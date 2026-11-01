# Incident lab: "payments are slow" (ST-29, scenario SC-05)

Goal: diagnose a production-style incident from evidence, in the order **metrics → traces → logs**, then write the incident record.

## 1. Start the observability stack
`APP_PROFILES=secure,docker,observability docker compose --profile apps --profile observability up -d --build` and `bash scripts/kibana-setup.sh`.
Run `bash scripts/demo.sh` once so there is baseline traffic. Open Grafana → **TicketHub / booking flow**.

## 2. Inject the incident
Make the (simulated) payment provider slow: create a booking and pay with `X-Simulate-Delay-Ms: 2500`
(under the 3 s timeout: slow but succeeds) several times, then `6000` (over the timeout):
```bash
for i in 1 2 3 4 5; do   # book + pay slowly; use a fresh seat/booking each loop
  curl -s -X POST localhost:8080/payments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
       -H 'X-Simulate-Delay-Ms: 2500' -d "{\"bookingId\":$ID}" -o /dev/null -w '%{http_code} %{time_total}s\n'
done
```

## 3. Investigate (write what you see at each step)
1. **Metrics (Grafana).** *P95 latency by endpoint* for `payment-service` `POST /payments` jumps to ~2.5 s+; request rate is unchanged;
   error rate rises only after the 6 s case (504). Hypothesis: the slowness is inside payment-service or after it, not in the gateway.
2. **Traces (Jaeger).** Service `api-gateway`, operation `POST /payments` → open a slow trace. The long span is inside `payment-service`
   (the provider call); the call to `booking-service` (`GET /internal/bookings/{id}`) is short. Hypothesis confirmed: provider call.
3. **Logs (Kibana).** Copy the trace's correlation id (response header `X-Correlation-Id`) → search `correlationId:"<id>"`. You see, in order:
   `GATEWAY_REQUEST` → `PAYMENT_INITIATED paymentId=… bookingId=…` → `PAYMENT_SUCCESS` (or `PAYMENT_PENDING (provider timeout)`).
   Search `bookingId:<n>` to see the whole business story across services.

## 4. Incident record (copy and fill)
| Field | Content |
|---|---|
| Time / detected by | |
| Symptom | Customers wait 3-6 s on "Pay"; some get 504 |
| Impact | Payments slow; PENDING payments waiting for webhooks |
| Diagnosis path | metrics (P95 payment) → trace (span in provider call) → logs (PAYMENT_PENDING provider timeout) |
| Root cause | Payment provider latency (injected delay) — not our DB, Booking, or gateway |
| Fix / mitigation | Hard 3 s timeout already stops thread exhaustion; payment stays PENDING and is resolved by webhook; circuit breaker opens if the provider keeps failing (fast 503) |
| Prevention | Alert on P95 payment latency and on `tickethub_payments_total{status="PENDING"}`; track provider SLA; add provider fallback (Phase 2) |
| Evidence | screenshots: Grafana panel, Jaeger trace, Kibana search; the `X-Correlation-Id` used |
