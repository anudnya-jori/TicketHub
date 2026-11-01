#!/usr/bin/env bash
# Scenario 14: reproduce each failure on the full Compose stack (docker compose --profile apps up -d, SECURE=false mode).
# Usage: bash scripts/chaos-lab.sh <name>      names: booking-down kafka-down webhooks expired-payment mail-down poison replay
# Each step prints what to check; recovery is automatic unless stated. Write the incident note in docs/INCIDENT_NOTES.md.
set -uo pipefail
GW=${GW:-http://localhost:8080}; H=(-H "X-Customer-Id: lab" -H "Content-Type: application/json")
KAFKA="docker exec -i tickethub-kafka /opt/kafka/bin"
psqlq() { docker exec tickethub-postgres psql -U tickethub -d "$1" -tAc "$2"; }
newbooking() {  # needs an OPEN show with seats; set SHOW and SEAT
  curl -s -X POST "$GW/bookings" "${H[@]}" -H "Idempotency-Key: lab-$RANDOM-$RANDOM" -d "{\"showId\":${SHOW:?set SHOW},\"seatIds\":[${SEAT:?set SEAT}]}" | sed -n 's/.*"bookingId":\([0-9]*\).*/\1/p'; }
case "${1:-}" in
 booking-down)   B=$(newbooking); docker compose stop booking-service
                 curl -s -X POST "$GW/payments" "${H[@]}" -d "{\"bookingId\":$B}"; echo
                 echo ">> money taken while Booking is down. Starting Booking: it must confirm by itself, one email."
                 docker compose start booking-service; sleep 45; curl -s "$GW/bookings/$B" "${H[@]}"; echo;;
 kafka-down)     B=$(newbooking); docker compose stop kafka
                 curl -s -X POST "$GW/payments" "${H[@]}" -d "{\"bookingId\":$B}"; echo
                 echo ">> outbox rows waiting:"; psqlq payment_db "select event_type,published_at from outbox_events where aggregate_id=$B"
                 docker compose start kafka; sleep 60; psqlq booking_db "select status from bookings where id=$B";;
 webhooks)       echo ">> send the same signed webhook 5x and out of order (see scripts/demo.sh WEBHOOK section for signing);"
                 echo ">> expect APPLIED once, DUPLICATE x4, final payment state unchanged by later conflicting events.";;
 expired-payment) B=$(newbooking); echo ">> wait for expiry (use the demo profile: reservation 1 minute), then pay:"; sleep 70
                 curl -s -X POST "$GW/payments" "${H[@]}" -d "{\"bookingId\":$B}"; echo
                 echo ">> expect BOOKING_EXPIRED (409) at pay time. For the late-event path insert a payment and let reconciliation run:"
                 curl -s -X POST localhost:8083/internal/reconciliation/run -H "X-Internal-Api-Key: ${INTERNAL_API_KEY:-dev-internal-key-change-me}"; echo;;
 mail-down)      B=$(newbooking); docker compose stop mailpit; curl -s -X POST "$GW/payments" "${H[@]}" -d "{\"bookingId\":$B}"; echo
                 sleep 20; psqlq notification_db "select status,error from notifications where booking_id=$B"
                 docker compose start mailpit; sleep 40; psqlq notification_db "select status from notifications where booking_id=$B"
                 echo ">> expect RETRYING then SENT, exactly one mail in http://localhost:8025";;
 poison)         echo '1:{"eventId":"poison-1","bookingId":1}' | $KAFKA/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic booking.confirmed --property parse.key=true --property key.separator=:
                 sleep 5; curl -s localhost:8084/internal/dead-letters -H "X-Internal-Api-Key: ${INTERNAL_API_KEY:-dev-internal-key-change-me}"; echo;;
 replay)         echo ">> start a NEW consumer group in replay mode (sends nothing):"
                 echo "   NOTIFICATION_GROUP_ID=replay-$RANDOM NOTIFICATION_REPLAY_MODE=true mvn -pl notification-service spring-boot:run"
                 echo ">> then: select status,count(*) from notifications group by 1;  (REPLAYED rows, zero new mails in Mailpit)";;
 *) sed -n 2,5p "$0";;
esac
