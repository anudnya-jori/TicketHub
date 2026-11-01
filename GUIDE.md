# TicketHub — Run Guide (all 13 Phase-1 epics)

Everything is already in this folder: you do not copy code from here, you build and run. Read `docs/ARCHITECTURE.md` to understand it
and `docs/DEMO_SCRIPT.md` to present it.

## 0. Install once
JDK **21** (`java -version`), Maven **3.9+** (`mvn -v`), Docker Desktop (running), curl, openssl. Windows: use **Git Bash** or **WSL** for the scripts.

## 1. Start the infrastructure
```bash
cp .env.example .env            # local passwords/secrets live here, never in Git
docker compose up -d            # PostgreSQL, Kafka, Keycloak, Mailpit, LocalStack(S3)
docker compose ps
```
| What | Where |
|---|---|
| PostgreSQL (4 databases) | localhost:5433 (user `tickethub`) |
| Kafka | localhost:9092 |
| Keycloak (login server) | http://localhost:8180 — realm `tickethub`; users `alice`/`alice123`, `bob`/`bob123` (CUSTOMER), `admin`/`admin123` (ADMIN) |
| Mailpit (fake email inbox) | http://localhost:8025 |
| LocalStack (fake AWS S3) | http://localhost:4566 |

## 2. Build and test
```bash
mvn clean verify
```
Database/Kafka-dependent tests use Testcontainers (Docker must be running; without Docker they are *skipped*, not failed).

## 3. Run the services — choose ONE way

### Way A — everything in containers (closest to production)
```bash
docker compose --profile apps up -d --build      # first build takes 5-10 min
docker compose ps                                  # wait until all are healthy
```
Services run with profiles `secure,docker`: JWT login is **required**.

### Way B — services on your machine (best for debugging in the IDE)
One terminal each, in this order. `secure` turns JWT security on (use the same value in every terminal):
```bash
export SPRING_PROFILES_ACTIVE=secure
export PAYMENT_WEBHOOK_SECRET=change-me-locally
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=us-east-1     # LocalStack dummy values
# Windows PowerShell:  $env:SPRING_PROFILES_ACTIVE="secure"; $env:AWS_ACCESS_KEY_ID="test"; ...
mvn -pl eureka-server spring-boot:run            # 8761  registry  http://localhost:8761
mvn -pl config-server spring-boot:run            # 8888  central config
mvn -pl event-show-service spring-boot:run       # 8081
mvn -pl booking-service spring-boot:run          # 8082
mvn -pl payment-service spring-boot:run          # 8083
NOTIFICATION_SENDER=smtp mvn -pl notification-service spring-boot:run     # 8084  (emails -> Mailpit)
mvn -pl api-gateway spring-boot:run              # 8080  the ONLY public door
```
(Config Server must be on `native`, so do not export `SPRING_PROFILES_ACTIVE=secure` in ITS terminal: run it as
`SPRING_PROFILES_ACTIVE=native mvn -pl config-server spring-boot:run`.)

Quick insecure mode (no Keycloak): leave `SPRING_PROFILES_ACTIVE` unset; identity then comes from the dev-only `X-Customer-Id` header
and the demo script is run with `SECURE=false bash scripts/demo.sh`.

Check: http://localhost:8761 lists all services, then `bash scripts/smoke.sh`.

## 4. Run the demo (the whole product, with assertions)
```bash
docker compose down -v && docker compose up -d     # fresh databases so ids start at 1 (restart the services after this)
bash scripts/demo.sh
```
It logs in through Keycloak and walks through ~45 checks: 401/403, catalogue, S3 poster upload (valid and fake), booking, idempotent retry,
seat conflict, payment, double-pay, ownership, declined payment, provider timeout → 504/PENDING, signed webhook / replay / bad signature.
At the end it prints `N passed, 0 failed`. Open Mailpit (http://localhost:8025) to see the confirmation email.

Get a token yourself:
```bash
TOKEN=$(curl -s -d client_id=tickethub-api -d grant_type=password -d username=alice -d password=alice123 \
  http://localhost:8180/realms/tickethub/protocol/openid-connect/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/events
```
Swagger UI per service: http://localhost:8081/swagger-ui.html (8082, 8083 likewise).

## 5. Resilience demo (Epic 9)
```bash
bash scripts/resilience-demo.sh      # needs Way A
```
Stops `event-show-service`, shows retries then the circuit breaker opening (calls fail fast with a controlled 503), then recovery.
The payment-timeout scenario is inside `demo.sh`.

## 6. Observability (Epic 11)
```bash
APP_PROFILES=secure,docker,observability docker compose --profile apps --profile observability up -d --build
bash scripts/kibana-setup.sh         # once
```
| Tool | URL | What to look at |
|---|---|---|
| Grafana | http://localhost:3000 (admin / `admin`) | dashboard **TicketHub / booking flow**: request rate, errors, P95, bookings, payments, circuit breaker, DB pool |
| Prometheus | http://localhost:9090 | Status → Targets |
| Jaeger | http://localhost:16686 | service `api-gateway` → one trace across gateway → booking → event-show |
| Kibana | http://localhost:5601 | Discover: `bookingId:1` or `correlationId:"…"` |

Every response carries `X-Correlation-Id`; the same id is in the logs of every service the request touched.
Incident exercise: `docs/INCIDENT_LAB.md`.

## 7. Cloud, release, evidence
- S3 / RDS / Secrets Manager: `docs/CLOUD.md` · Release/rollback/deploy: `docs/DEPLOYMENT.md` (`scripts/release.sh`, `scripts/rollback.sh`)
- Evidence checklist per story: `docs/EVIDENCE.md`

## 8. What is implemented (by epic)
| Epic | Stories | Where |
|---|---|---|
| 0 Delivery foundation | ST-45.. repo, PR template, CI (build, test, images) | `.github/`, root |
| 1 Product/domain | docs, UML, entities | `docs/ARCHITECTURE.md` |
| 2 Event & Show | ST-03/04/48/49/05 | `event-show-service` |
| 3 Booking | ST-06/07/08/09/52/53 | `booking-service` |
| 4 Payment | ST-20..24/54/55 | `payment-service` |
| 5 Notification | ST-19/37 | `notification-service` |
| 6 Communication & API quality | ST-47/50/51 (REST clients, error contract, OpenAPI) | all services |
| 7 Platform | Eureka, Config Server, Gateway (ST-10..13) | `eureka-server`, `config-server`, `api-gateway` |
| 8 Security | ST-15 Keycloak, ST-14 JWT, ST-59 roles + ownership, internal service key | all services, `docker/keycloak` |
| 9 Resilience | ST-16 timeouts, ST-17 bounded retry (reads only), ST-18 circuit breaker + fast-fail | `*/resilience`, `payment-service/provider` |
| 10 Events | Kafka booking-confirmed, dedupe, failure isolation | booking → notification |
| 11 Observability | ST-25 JSON logs + correlation id, ST-26 ELK, ST-27 Prometheus/Grafana, ST-28 OpenTelemetry/Jaeger, ST-29 incident lab | `docker/observability`, `docs/INCIDENT_LAB.md` |
| 12 Cloud | ST-30 S3 posters, ST-31 RDS + secrets (documented) | `event-show-service/.../media`, `docs/CLOUD.md` |
| 13 Containers/CI/deploy | ST-61/32/33/34/62 | `docker-compose.yml`, `Dockerfile`, `scripts/` |

## 9. Honest status (read before presenting)
- Checked by the author: Java syntax of every module; all four SQL migrations on real PostgreSQL 16 including a 8-way concurrent insert for one seat
  (exactly one wins); shell scripts' syntax; docker-compose YAML parses.
- **Not run by the author:** the Maven build, the Spring tests, the Docker images, Keycloak/Kafka/S3/ELK containers, `demo.sh` (the build
  sandbox could not download Maven dependencies or run Docker). Expect to fix small first-run problems: run `mvn clean verify` first and
  give the first error to whoever is helping you.
- The payment provider is **simulated** (no real sandbox); AWS usage is LocalStack locally. Real AWS needs your account: see `docs/CLOUD.md`.
- Phase 2 (scenarios 0-6) is added: see docs/PHASE2.md. Scenarios 7-14 are not implemented.

## 10. Troubleshooting
- *Port in use* → stop the other process or change `server.port`.
- *401 on everything* → you are running with `secure`; get a token (section 4) or run insecure mode.
- *Booking returns 503 EVENT_SHOW_SERVICE_UNAVAILABLE* → Event/Show not registered yet (wait ~30 s, check Eureka), or the circuit breaker is open (wait 10 s).
- *Poster upload returns 503 MEDIA_STORAGE_UNAVAILABLE* → LocalStack not running, or `AWS_ACCESS_KEY_ID/SECRET` not exported for event-show.
- *Kafka warnings* → `docker compose ps`: kafka must be healthy. Booking still works without Kafka (`EVENT_PUBLISH_FAILED` is logged).
- *Flyway "validate failed"* → an applied migration was edited: `docker compose down -v && docker compose up -d`.
- *Tests skipped* → Docker is not running.
- *Gateway returns 502/503 right after start* → services need ~30 s to register with Eureka.
