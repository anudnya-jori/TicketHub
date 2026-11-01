#!/usr/bin/env bash
# TicketHub end-to-end demo + acceptance test (ST-62). Goes through the API Gateway with real Keycloak JWTs.
# Prints each step and PASS/FAIL; exits 1 if anything fails. Works on Linux/macOS/Git-Bash/WSL (needs curl, openssl).
#
#   Fresh start:  docker compose down -v && docker compose up -d      (then start the services; see GUIDE.md)
#   Run:          bash scripts/demo.sh
# Env: BASE (gateway, default http://localhost:8080)  KC (Keycloak, default http://localhost:8180)
#      PAYMENT_WEBHOOK_SECRET (must equal the payment-service's)  SECURE=false to run against services started WITHOUT the
#      "secure" profile (identity then comes from X-Customer-Id headers, a dev-only fallback)
BASE=${BASE:-http://localhost:8080}
KC=${KC:-http://localhost:8180}
PAY_SECRET=${PAYMENT_WEBHOOK_SECRET:-change-me-locally}
SECURE=${SECURE:-true}
PASS=0; FAIL=0; BODY=$(mktemp); trap 'rm -f "$BODY" /tmp/tickethub-poster.*' EXIT

say()  { printf '\n\033[1m== %s\033[0m\n' "$1"; }
jget() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" "$BODY" | head -1; }
# req METHOD PATH [JSON] [extra curl args...]  -> sets CODE, writes BODY
req() { local m=$1 p=$2 d=$3; shift 3
  if [ -n "$d" ]; then CODE=$(curl -s -o "$BODY" -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json' -d "$d" "$@")
  else CODE=$(curl -s -o "$BODY" -w '%{http_code}' -X "$m" "$BASE$p" "$@"); fi
  printf '  %s %s -> HTTP %s  %s\n' "$m" "$p" "$CODE" "$(head -c 170 "$BODY" | tr '\n' ' ')"; }
# expect LABEL EXPECTED_CODE [substring]
expect() { local ok=1; [ "$CODE" = "$2" ] || ok=0
  if [ -n "$3" ] && ! grep -q -- "$3" "$BODY"; then ok=0; fi
  if [ $ok = 1 ]; then PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"
  else FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s (expected %s %s)\n' "$1" "$2" "$3"; fi; }

token() { curl -s -d client_id=tickethub-api -d grant_type=password -d "username=$1" -d "password=$2" \
  "$KC/realms/tickethub/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }

if [ "$SECURE" = true ]; then
  say "LOGIN  get tokens from Keycloak (alice, bob = CUSTOMER; admin = ADMIN)"
  TA=$(token admin admin123); TAL=$(token alice alice123); TB=$(token bob bob123)
  [ -n "$TA" ] && [ -n "$TAL" ] && [ -n "$TB" ] || { echo "Could not get tokens from $KC - is Keycloak up? (docker compose up -d keycloak)"; exit 1; }
  ADMIN=(-H "Authorization: Bearer $TA"); ALICE=(-H "Authorization: Bearer $TAL"); BOB=(-H "Authorization: Bearer $TB")
  echo "  tokens obtained"
else
  ADMIN=(); ALICE=(-H 'X-Customer-Id: alice' -H 'X-Customer-Email: alice@example.com'); BOB=(-H 'X-Customer-Id: bob')
fi

if [ "$SECURE" = true ]; then
  say "ST-14  no token -> 401;  ST-59  customer cannot administer -> 403;  internal API not reachable from outside"
  req GET /events "" ;                                               expect "no token is rejected" 401 UNAUTHENTICATED
  req POST /events '{"title":"Hack","type":"MOVIE"}' "${ALICE[@]}";  expect "CUSTOMER cannot create events" 403 FORBIDDEN
  req GET /internal/bookings/1 "" "${ADMIN[@]}";                      expect "/internal/** is not routed by the gateway" 404
fi

say "EPIC 2  catalogue (ADMIN): venue, seats, event, show"
req POST /venues '{"name":"PVR Cinemas","location":"Pune"}' "${ADMIN[@]}";                              expect "venue created" 201
VENUE=$(jget id)
req POST /venues/$VENUE/seats '{"seats":[{"row":"A","number":1},{"row":"A","number":2},{"row":"A","number":3},{"row":"B","number":1,"category":"VIP"}]}' "${ADMIN[@]}"; expect "seats created" 201
req POST /events '{"title":"Dune: Part Two","description":"Sci-fi","type":"MOVIE"}' "${ADMIN[@]}";       expect "event created as DRAFT" 201 DRAFT
EVENT=$(jget id)
req POST /shows "{\"eventId\":$EVENT,\"venueId\":$VENUE,\"startTime\":\"2030-01-01T10:00:00Z\",\"endTime\":\"2030-01-01T12:30:00Z\",\"price\":250.00,\"currency\":\"INR\"}" "${ADMIN[@]}"; expect "show created" 201
SHOW=$(jget id)
req POST /shows/$SHOW/open "" "${ADMIN[@]}";                    expect "cannot open a show while the event is DRAFT" 409 EVENT_NOT_PUBLISHED
req POST /events/$EVENT/publish "" "${ADMIN[@]}";               expect "event published" 200 PUBLISHED
req POST /shows/$SHOW/open "" "${ADMIN[@]}";                    expect "show opened" 200 OPEN
req GET /events "" "${ALICE[@]}";                               expect "customer sees the published event" 200 "Dune"

say "ST-30  poster upload to S3 (LocalStack): valid PNG, then a fake PNG"
PNG=$(mktemp /tmp/tickethub-poster.XXXXXX)
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==' | base64 -d > "$PNG" 2>/dev/null
CODE=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$BASE/events/$EVENT/poster" "${ADMIN[@]}" -F "file=@$PNG;type=image/png"); echo "  POST poster -> HTTP $CODE"
expect "PNG poster stored, event keeps its object reference" 200 posters/event-$EVENT/
echo 'not an image' > "$PNG"
CODE=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$BASE/events/$EVENT/poster" "${ADMIN[@]}" -F "file=@$PNG;type=image/png"); echo "  POST fake png -> HTTP $CODE"
expect "file whose content is not a PNG is rejected" 415 FILE_CONTENT_MISMATCH
req GET /events/$EVENT/poster "" "${ALICE[@]}";                 expect "customer can download the poster" 200

say "EPIC 3  Alice books seats 1 and 2 (price snapshot, 10-minute hold)"
SEATS=$(curl -s "$BASE/venues/$VENUE/seats" "${ALICE[@]}" | sed 's/},{/}\n{/g' | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | tr '\n' ' ')
set -- $SEATS; S1=$1; S2=$2; S3=$3; S4=$4
KEY="alice-$RANDOM-$RANDOM"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S1,$S2]}" "${ALICE[@]}" -H "Idempotency-Key: $KEY"; expect "booking created, PENDING_PAYMENT, amount 500" 201 PENDING_PAYMENT
B1=$(jget bookingId); grep -q '"amount":500' "$BODY" && echo "  amount = 250 x 2 = 500"
say "ST-53  same request + same Idempotency-Key -> same booking, no duplicate"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S1,$S2]}" "${ALICE[@]}" -H "Idempotency-Key: $KEY"; expect "retry returns the original booking (200)" 200 "\"bookingId\":$B1"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S3]}" "${ALICE[@]}" -H "Idempotency-Key: $KEY";       expect "same key, different request is refused" 409 IDEMPOTENCY_KEY_REUSED
say "ST-07  Bob wants a seat Alice holds -> 409"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S2]}" "${BOB[@]}" -H "Idempotency-Key: bob-$RANDOM";  expect "seat already reserved" 409 SEAT_UNAVAILABLE
req GET /shows/$SHOW/seats "" "${ALICE[@]}";                                                              expect "seat map shows RESERVED seats" 200 RESERVED

say "EPIC 4  Alice pays (simulated provider) -> booking CONFIRMED"
req POST /payments "{\"bookingId\":$B1}" "${ALICE[@]}";                         expect "payment succeeded" 201 SUCCESS
P1=$(jget paymentId)
req POST /payments "{\"bookingId\":$B1}" "${ALICE[@]}";                         expect "paying twice returns the same payment (no 2nd charge)" 200 "\"paymentId\":$P1"
sleep 4   # Phase 2: Payment -> outbox -> Kafka -> Booking is asynchronous; give the saga a moment
req GET /bookings/$B1 "" "${ALICE[@]}";                                         expect "booking is CONFIRMED (via PaymentSucceeded event)" 200 CONFIRMED
say "ST-59  ownership: Bob cannot see or cancel Alice's booking / payment"
req GET /bookings/$B1 "" "${BOB[@]}";                                           expect "Bob cannot view Alice's booking" 404 BOOKING_NOT_FOUND
req GET /payments/$P1 "" "${BOB[@]}";                                           expect "Bob cannot view Alice's payment" 404 PAYMENT_NOT_FOUND
req POST /bookings/$B1/cancel "" "${ALICE[@]}";                                 expect "Phase 2: cancelling a confirmed booking is allowed, refund REQUESTED" 200 REQUESTED
sleep 4   # BookingCancelled -> Payment refunds -> PaymentRefunded -> Booking records REFUNDED
req GET /bookings/$B1 "" "${ALICE[@]}";                                         expect "booking CANCELLED and the money REFUNDED" 200 REFUNDED
req GET /payments/$P1 "" "${ALICE[@]}";                                         expect "payment is REFUNDED" 200 REFUNDED
echo "  (Epic 5) confirmation email: open http://localhost:8025 (Mailpit) - or look for NOTIFICATION_SENT in the notification-service log"

say "FAILURE  Bob's payment is declined -> seats are released"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S3]}" "${BOB[@]}" -H "Idempotency-Key: bob-$RANDOM"; B2=$(jget bookingId)
req POST /payments "{\"bookingId\":$B2}" "${BOB[@]}" -H 'X-Simulate-Outcome: FAILED';  expect "payment FAILED" 201 FAILED
sleep 4   # PaymentFailed event -> Booking
req GET /bookings/$B2 "" "${BOB[@]}";                                           expect "booking is PAYMENT_FAILED" 200 PAYMENT_FAILED
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S3]}" "${ALICE[@]}" -H "Idempotency-Key: alice-$RANDOM"; expect "the released seat can be booked again" 201

say "ST-16  TIMEOUT  slow provider -> 504, payment PENDING (never 'failed', never charged twice)"
req POST /bookings "{\"showId\":$SHOW,\"seatIds\":[$S4]}" "${BOB[@]}" -H "Idempotency-Key: bob-$RANDOM"; B3=$(jget bookingId)
req POST /payments "{\"bookingId\":$B3}" "${BOB[@]}" -H 'X-Simulate-Delay-Ms: 6000'; expect "controlled 504 timeout error" 504 PAYMENT_PROVIDER_TIMEOUT
req POST /payments "{\"bookingId\":$B3}" "${BOB[@]}";                           expect "retry does not create a second payment" 200 PENDING
say "WEBHOOK  the provider later reports the result (HMAC-signed)"
REF=$(docker exec tickethub-postgres psql -U tickethub -d payment_db -tAc "select provider_reference from payments where booking_id=$B3" 2>/dev/null | tr -d '[:space:]')
if [ -n "$REF" ]; then
  EVT="evt-$RANDOM"; WB="{\"eventId\":\"$EVT\",\"providerReference\":\"$REF\",\"result\":\"SUCCESS\"}"
  SIG=$(printf '%s' "$WB" | openssl dgst -sha256 -hmac "$PAY_SECRET" | sed 's/^.* //')
  req POST /payments/webhook "$WB" -H "X-Signature: $SIG";                      expect "valid webhook applied" 200 APPLIED
  req POST /payments/webhook "$WB" -H "X-Signature: $SIG";                      expect "replayed webhook is a duplicate" 200 DUPLICATE
  req POST /payments/webhook "$WB" -H "X-Signature: deadbeef";                  expect "bad signature rejected" 401 INVALID_SIGNATURE
  sleep 4; req GET /bookings/$B3 "" "${BOB[@]}";                                expect "booking CONFIRMED after the webhook" 200 CONFIRMED
else echo "  (skipped: needs the docker postgres container 'tickethub-postgres' to read the provider reference)"; fi

say "RESULT"
printf '  %s passed, %s failed\n' "$PASS" "$FAIL"
[ "$FAIL" = 0 ]
