# Cloud: S3, RDS, Secrets (ST-30, ST-31)

## S3 posters (ST-30) — implemented
- Code: `event-show-service/.../media/S3MediaStorage`, `service/PosterService`, `controller/PosterController`, `config/S3Config`.
- API: `POST /events/{id}/poster` (ADMIN, multipart field `file`), `GET /events/{id}/poster` (any logged-in user).
- Rules: JPEG/PNG/WebP only, ≤ 2 MB, file content must match the declared type (magic bytes), stored as `posters/event-{id}/{uuid}.{ext}`,
  server-side encryption (AES256); the event stores only the object key (`posterRef`).
- The bucket is **private** (Block Public Access is applied at startup in dev). Files are served through the service, so login/role rules apply.
- Credentials are **never in the repo**: the AWS default provider chain reads `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (LocalStack uses dummy `test`)
  or an **IAM role** on AWS.
- Local: LocalStack (`docker compose up -d`). Real AWS: set `S3_ENDPOINT=` (empty), `S3_BUCKET`, `AWS_REGION`, `S3_CREATE_BUCKET=false`
  and attach an IAM role to the task/instance with this policy (least privilege):
```json
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],
 "Resource":"arn:aws:s3:::YOUR-BUCKET/posters/*"},{"Effect":"Allow","Action":"s3:ListBucket","Resource":"arn:aws:s3:::YOUR-BUCKET"}]}
```
Create the bucket with *Block all public access* ON and default encryption ON.

## RDS + Secrets Manager (ST-31) — deployment procedure (needs your AWS account; not executed here)
1. **RDS:** create 4 PostgreSQL 16 databases (one RDS instance with 4 databases is fine for a student environment, or 4 instances):
   `eventshow_db`, `booking_db`, `payment_db`, `notification_db`; private subnets, security group allowing only the services; automated backups ON.
2. **Secrets:** create secrets in AWS Secrets Manager (or SSM Parameter Store): `tickethub/db` (username/password), `tickethub/payment-webhook-secret`,
   `tickethub/internal-api-key`, provider credentials. Grant the service role `secretsmanager:GetSecretValue` on those ARNs only.
3. **Inject at deploy time**, not build time: ECS task definition `secrets` (or Kubernetes External Secrets) map them to the env vars the services already read:
   `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PAYMENT_WEBHOOK_SECRET`, `INTERNAL_API_KEY`. No code change is needed because services read env vars.
   (Alternative: Spring Cloud AWS `spring.config.import=aws-secretsmanager:tickethub/db`.)
4. **Verify no secret leaked:** `git log -p | grep -iE "password|secret"` shows only dev placeholders; `docker history <image>` shows no credentials
   (images contain no secrets: they come from env at runtime); Config Server repo has no passwords (a test enforces it).
5. Flyway runs the migrations on first start against RDS (`spring.flyway.enabled=true`).
