# Authentication & authorization – deployment and migration notes

## What changed
- `POST /api/auth/register`, `/login` and `/verify-otp` (existing user) now also return `token` and `expiresIn` (seconds).
  The existing response fields are unchanged. Tokens are HS256 JWTs (Spring Security OAuth2 Resource Server / Nimbus),
  default lifetime 30 minutes, **no refresh tokens** – users sign in again after expiry.
- Every protected request validates signature, expiry and issuer, then re-checks the user and role in the database
  (a deleted user or changed role invalidates old tokens immediately).
- Roles are enforced by Spring Security:
  - public: `/api/auth/**`, `GET /api/products/**`, `GET /api/wholesaler/products/**`, SPA files, `/actuator/health`
  - `RETAILER`: writes under `/api/products/**` and `GET /api/products/mine`
  - `WHOLESALER`: writes under `/api/wholesaler/products/**` and `GET /api/wholesaler/products/mine`
  - any other `/api/**` route requires authentication
- Ownership: the owner is taken from the verified principal. Update/delete by a non-owner returns **403**, unknown ids **404**.
  Client-supplied `id`/`ownerId` in request bodies are ignored.
- OTP: a code is burned after 5 wrong guesses (a correct code now yields a token).
- Google/Facebook login remains disabled (`/api/auth/social-login` returns 501).

## Required configuration (Render / Docker)
| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | **yes** | random, at least 32 bytes, e.g. `openssl rand -base64 48`. The app refuses to start without it. Rotating it logs everyone out. |
| `JWT_ISSUER` | no | default `emart` |
| `JWT_TTL` | no | ISO-8601 duration, default `PT30M` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | for real OTP email | see `application.properties` |

**Deploying the backend without `JWT_SECRET` set will crash on boot. Deploy backend and frontend together:** the new backend
rejects the old frontend's unauthenticated product writes (401), and the old frontend does not send tokens.

## Database migration (additive only, nothing is dropped)
Hibernate `ddl-auto=update` adds two **nullable** columns; no existing data is changed or removed:
- `product.owner_id`, `wholesaler_product.owner_id` (integer)
- `otp.failed_attempts` (integer)

If you manage schema yourself (e.g. Postgres without `update`):
```sql
ALTER TABLE product              ADD COLUMN owner_id integer;
ALTER TABLE wholesaler_product   ADD COLUMN owner_id integer;
ALTER TABLE otp                  ADD COLUMN failed_attempts integer;
```
**Existing products have `owner_id = NULL`.** They stay publicly readable but nobody can update or delete them until an owner is
assigned (fail-closed). Assign owners once, after checking who owns what, e.g.:
```sql
UPDATE product            SET owner_id = <retailer users.id>   WHERE owner_id IS NULL;
UPDATE wholesaler_product SET owner_id = <wholesaler users.id> WHERE owner_id IS NULL;
```
(The default H2 database is in-memory, so there is nothing to migrate there.)

## Known limitations / follow-ups
- No rate limiting on login or OTP send; no refresh/revocation list beyond the per-request user/role check.
- Wholesaler catalogue reads are still public (unchanged behaviour).
- `AuthService.sendOtp` prints the OTP to the console if email sending fails – remove before production use.
- `frontend/.env` is tracked in git; it holds public client IDs only, but should be untracked.
- Build on JDK 24 needs `-Dnet.bytebuddy.experimental=true` for Mockito (set in the surefire config); the Docker image targets 21.
