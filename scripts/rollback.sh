#!/usr/bin/env bash
# Re-tag the previous release as "latest" and restart. Database migrations are forward-only: only roll back
# releases whose migrations are backward compatible (see docs/RUNBOOK.md).
set -euo pipefail
PREV=$(cat .previous-release 2>/dev/null || true)
[ -n "$PREV" ] || { echo "No previous release recorded."; exit 1; }
for m in eureka-server config-server api-gateway event-show-service booking-service payment-service notification-service; do
  docker tag tickethub/$m:$PREV tickethub/$m:latest
done
docker compose --profile apps up -d --no-build
echo "$PREV" > .current-release
echo "Rolled back to $PREV"
