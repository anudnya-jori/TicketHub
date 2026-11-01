# Runbook
- **Start/stop:** `docker compose up -d` / `docker compose down` (add `-v` to wipe data).
- **Health:** `scripts/smoke.sh`; Eureka http://localhost:8761; each service `/actuator/health`.
- **Release:** `scripts/release.sh v1.0.0` builds+tests+tags images. **Rollback:** `scripts/rollback.sh` (re-tags previous images).
  Migrations are forward-only: roll back only when the newer migration is backward compatible.
- **Seats stuck reserved?** The expiry scheduler runs every 30 s (`booking.expiry.*`); look for "Expired N unpaid booking(s)".
- **Payment PENDING?** The provider timed out; it resolves by webhook. Unsynced results retry every 15 s (`PAYMENT_SYNC_PENDING` in logs).
- **Confirmation email missing?** Check `GET /internal/notifications?bookingId=` (SENT/FAILED), Kafka health, Mailpit :8025.
- **Secrets:** never commit `.env`; `PAYMENT_WEBHOOK_SECRET` and DB password come from environment.
