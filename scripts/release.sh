#!/usr/bin/env bash
# Build + tag + start a release. Usage: scripts/release.sh v1.0.0
set -euo pipefail
VERSION=${1:?usage: release.sh <version>}
echo "$VERSION" > .last-release-pending
mvn -B verify
for m in eureka-server config-server api-gateway event-show-service booking-service payment-service notification-service; do
  docker build --build-arg MODULE=$m -t tickethub/$m:$VERSION -t tickethub/$m:latest .
done
# remember the previous release so rollback.sh knows where to go back to
[ -f .current-release ] && cp .current-release .previous-release || true
echo "$VERSION" > .current-release; rm -f .last-release-pending
echo "Built release $VERSION. Start: docker compose --profile apps up -d"
