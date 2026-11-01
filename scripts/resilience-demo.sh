#!/usr/bin/env bash
# Resilience demo (ST-16/17/18, scenarios SC-05 / SC-16). Needs the full stack in Docker (docker compose --profile apps up -d)
# and a FRESH catalogue created by scripts/demo.sh (show id 1, seats exist). Watch the booking-service log in another terminal:
#   docker compose logs -f booking-service | grep -E "CIRCUIT_BREAKER|unavailable"
BASE=${BASE:-http://localhost:8080}; KC=${KC:-http://localhost:8180}
TOKEN=$(curl -s -d client_id=tickethub-api -d grant_type=password -d username=alice -d password=alice123 \
  "$KC/realms/tickethub/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
book() { curl -s -o /dev/null -w '%{http_code} in %{time_total}s\n' -X POST "$BASE/bookings" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: res-$RANDOM-$RANDOM" -d '{"showId":1,"seatIds":[1]}'; }

echo "1) Normal: dependency healthy"; book
echo; echo "2) Kill the dependency: stop event-show-service"; docker compose stop event-show-service >/dev/null
echo "   First calls are slow: every attempt is retried (bounded) before giving up -> controlled 503"
for i in 1 2 3 4 5 6; do printf '   call %s: ' $i; book; done
echo "   After enough failures the circuit OPENS: calls now fail FAST (see the time) with the same controlled 503"
for i in 7 8 9; do printf '   call %s: ' $i; book; done
echo; echo "3) Recovery: start event-show-service again and wait for the breaker to half-open (10 s) + service to register"
docker compose start event-show-service >/dev/null; sleep 45
for i in 1 2 3; do printf '   call %s: ' $i; book; done
echo "   (201 / 409 means normal traffic is back: 409 SEAT_UNAVAILABLE is a normal business answer, not a failure)"
