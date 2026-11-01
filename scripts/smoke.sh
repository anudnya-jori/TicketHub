#!/usr/bin/env bash
# Smoke test: every service answers /status (and the gateway routes work). Exit code 1 on any failure.
fail=0
for p in 8081 8082 8083 8084; do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$p/status)
  echo "port $p /status -> $code"; [ "$code" = 200 ] || fail=1
done
for path in /events /bookings/1 ; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Customer-Id: smoke' http://localhost:8080$path)
  echo "gateway $path -> $code"; [ "$code" != 502 ] && [ "$code" != 503 ] || fail=1
done
curl -s http://localhost:8761/eureka/apps -H 'Accept: application/json' | grep -o '"name":"[A-Z-]*"' | sort -u
exit $fail
