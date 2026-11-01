# Deployment and release (ST-32, ST-33, ST-34, ST-62)

## Images (ST-32)
One Dockerfile for all services with pinned bases (`maven:3.9.9-eclipse-temurin-21`, `eclipse-temurin:21.0.5_11-jre`), non-root user:
`docker build --build-arg MODULE=booking-service -t tickethub/booking-service:v1.0.0 .`
Health checks are in `docker-compose.yml`. CI (`.github/workflows/ci.yml`) runs `mvn verify`, builds every image, and validates the compose file.
Full stack, one command: `docker compose --profile apps up -d --build`.

## Integration deployment (ST-33) — repeatable procedure
1. Merge to `main` (CI green). 2. On the integration host: `git pull && cp .env.example .env` (edit secrets) 3. `docker compose --profile apps up -d --build`
4. `bash scripts/smoke.sh` (must exit 0) 5. record: date, commit SHA, smoke output, who deployed (copy into `docs/EVIDENCE.md`).

## Release and rollback (ST-34)
```bash
git tag v1.0.0 && bash scripts/release.sh v1.0.0      # mvn verify + build/tag images tickethub/<svc>:v1.0.0 and :latest
docker compose --profile apps up -d --no-build        # deploy
bash scripts/smoke.sh                                 # verify
# ... later, v1.1.0 is bad:
bash scripts/rollback.sh                              # re-tags the previous release as latest and restarts
bash scripts/smoke.sh                                 # verify the rollback
```
Database rule: migrations are forward-only. Only ship a release whose migration is backward compatible with the previous version
(add columns/tables, never drop/rename in the same release) so rollback is safe.

## Production-style end-to-end run (ST-62)
`bash scripts/demo.sh` (gateway + JWT + payment + Kafka + email), `bash scripts/resilience-demo.sh` (one resilience scenario), and the
incident lab (`docs/INCIDENT_LAB.md`, one observability investigation). Everything runs in Docker (`--profile apps --profile observability`).
