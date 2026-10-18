# E-Mart – demo readiness, deployment & rollback

Status of this document: written 2026-10-18 from **local** testing only. Nothing here was verified against the live Render service.

## 1. Test results (local, Windows, JDK 24, Node 24)
| Check | Result |
|---|---|
| `mvn clean verify` (backend) | PASS – 53 tests, 0 failures (`-Dnet.bytebuddy.experimental=true` set in surefire because Mockito/ByteBuddy of Boot 3.2 predates JDK 24) |
| `npm ci` + `npm run build` (frontend) | PASS |
| Local API smoke (packaged jar, random `JWT_SECRET`) | PASS: register ×3 roles, duplicate email → 400, invalid role → 400, login, bad password → 401, OTP send/verify → token, product create/read/update/delete, image upload + public image fetch, anonymous write → 401, customer write → 403, cross-owner update/delete → 403, social login → 501, `/actuator/env` → 401 |
| Startup without `JWT_SECRET` | Fails fast with a clear message (intended) |
| Docker image build | **NOT RUN** – Docker is not installed on this machine. Dockerfile inspected only (below). |
| Browser/UI click-through | PASS (local, Vite dev server + jar): retailer register/login, create with image, edit (detail page and Edit Stock page), delete, low-stock page, customer login + browse, wholesaler register/login/create, expired-token redirect with notice, cross-owner edit shows the permission message, no social-login buttons shown |
| Low-stock filtering | PASS in browser: it is a **frontend feature** (`/retailer/low-stock`, quantity < 10, plus the Refill Stock badge) over `GET /api/products/mine`. There is no backend endpoint for it. (An earlier version of this document wrongly said it did not exist.) |
| Live Render | **NOT TESTED** |

Dockerfile review: 3 stages (node:20 build → maven + temurin 21 build with the React `dist` copied into `static/` → temurin 21 JRE). Looks consistent with the app (Java 21, `PORT` honoured, `-DskipTests` in the image build so tests do not run there). It will start only if `JWT_SECRET` is provided at runtime.

## 2. Environment variables
| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | **Yes** | HS256 signing key, ≥ 32 random bytes (`openssl rand -base64 48`). App refuses to start without it. Changing it logs everyone out. |
| `JWT_ISSUER` | No (`emart`) | token issuer claim |
| `JWT_TTL` | No (`PT30M`) | token lifetime, ISO-8601 |
| `PORT` | Set by Render | HTTP port |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | For real OTP emails | Gmail SMTP user and **app password** |
| `MAIL_FROM` | No | From address (defaults to `MAIL_USERNAME`) |
| `H2_CONSOLE_ENABLED` | No (`false`) | exposes the H2 console; keep off in production |
| `VITE_ENABLE_GOOGLE_LOGIN`, `VITE_ENABLE_FACEBOOK_LOGIN`, `VITE_GOOGLE_CLIENT_ID`, `VITE_FACEBOOK_APP_ID` | No | **Build-time** frontend flags; social login is off unless a flag is `true`, and the server rejects social login anyway (501) |

Without `MAIL_*`, OTP emails fail and the code is printed to the server log instead (usable for a local demo, not for production).

## 3. H2 in-memory database limitation
The datasource is `jdbc:h2:mem:emartDB`. **All users, products, images and OTPs live in RAM and vanish on every restart, redeploy, crash, or Render free-tier spin-down/idle sleep.** Verified locally: after a restart, login → 401 and the product list is empty. Consequences for the demo:
- Register the demo accounts and add products **right before** presenting; do not rely on data created earlier.
- If the service sleeps mid-demo, accounts disappear and previously issued tokens become invalid (401 → the app sends the user to the login page).
- Persisting data needs an external database (e.g. Render Postgres: the Postgres driver is already a dependency; datasource URL/user/password would come from env vars). That change is **not made**.
- Also single instance only: do not scale to more than one instance (each would have its own empty database).

## 4. Secret scan
- Tracked files and my changes: no mail password, JWT secret, private key or API key found (pattern scan of `backend/src`, `frontend/src`, docs, Dockerfile). The only secret-like fixture is the test-profile JWT string in `src/test/resources/application-test.properties`, used by tests only.
- **Git history still contains a real Gmail app password** (the original `application.properties`, commit `62a8e04`/`909c193`). It is no longer in the working tree but is recoverable from GitHub. **Revoke it in the Google account** and create a new one for `MAIL_PASSWORD`. Removing it from history needs a history rewrite, which I did not do.
- `frontend/.env` is tracked. It holds a Google client ID and Facebook app ID (public identifiers, not secrets) but should be untracked and its IDs rotated if you do not control them.

## 5. Pre-demo checklist
- [ ] `JWT_SECRET` set (and `MAIL_*` if you want real OTP emails; otherwise plan to show OTP from the log or skip OTP).
- [ ] Gmail app password that was in git history revoked.
- [ ] Open the live URL ~10 minutes early to wake the free instance; confirm `GET /` loads and `/api/products` answers.
- [ ] Register fresh demo users: one CUSTOMER, one RETAILER, one WHOLESALER; add 2–3 products (one with an image).
- [ ] Rehearse: customer browses → retailer adds/edits/deletes own stock → second retailer cannot change it (403 message) → expired/invalid token returns to login.
- [ ] Do **not** demo Google/Facebook login (disabled by design) or low-stock filtering (not implemented).
- [ ] Keep a local run ready as a fallback (see below).
- [ ] Know that a restart wipes everything (section 3).

Local fallback run:
```bash
cd backend && mvn clean package -DskipTests
JWT_SECRET=$(openssl rand -base64 48) java -jar target/backend-1.0.0.jar   # API on :8080
cd ../frontend && npm ci && npm run dev                                       # UI on :3000
```
(The dev server needs a proxy to the backend as configured in `vite.config.js`; the Docker image instead serves the built UI from Spring Boot.)

## 6. Deployment checklist (to do manually; not done)
1. Create a branch, review `git diff`, commit, open a PR (nothing is committed yet).
2. On Render, add `JWT_SECRET` (and `MAIL_*`) **before** deploying – otherwise the new build crashes on boot.
3. Deploy backend + frontend together (single Docker image does both). The old UI cannot write products against the new API.
4. Watch the deploy log for `Started EmartApplication`; then `GET /api/products` → 204 and `POST /api/products` without a token → 401.
5. Re-register demo data (H2 is empty after deploy).
6. Run the demo script; check browser console for 401/403 loops.
7. Schema changes are additive nullable columns (see `AUTH_MIGRATION.md`); no data migration is needed for the in-memory DB.

## 7. Rollback plan
- **Fast:** in Render, redeploy the previous successful deploy (Deploys → previous → Rollback). Because data is in memory it is lost either way; the old build does not need `JWT_SECRET`.
- **Git:** `git revert <merge-commit>` (do not force-push) and redeploy.
- The old version had **no authentication on product writes**, so rolling back reopens that hole; only do it for a demo emergency and re-apply promptly.
- If login suddenly fails after deploy: check `JWT_SECRET` is set and unchanged between instances/restarts.
- Any env var added for this release (`JWT_*`) can stay set; it is ignored by the old build.

## 8. Known bugs and security limitations
**Security**
- Git history leaks the old Gmail app password (revoke it).
- No rate limiting on login, registration or OTP sending (OTP verification itself is capped at 5 wrong guesses per code, but a new code can be requested repeatedly).
- Tokens live in `localStorage` (readable by any XSS), last 30 minutes, cannot be revoked individually, and there is no refresh/logout-on-server.
- OTP code is written to the server log when email delivery fails.
- Wholesaler catalogue (and its prices) is publicly readable; products have no ownership display.
- Existing/legacy products without an owner can be neither edited nor deleted (fail-closed) until owners are assigned.
- CORS allows only localhost origins; in production the SPA is same-origin so this is fine, but a separate frontend host would be blocked.
- Frontend role checks (`localStorage`) are cosmetic only; the server enforces roles.
- `npm audit` (prod deps): 6 findings (axios, @remix-run/router/react-router, follow-redirects). axios is declared but the code uses `fetch`; react-router upgrade is advisable post-demo.
- Social login is intentionally disabled (needs server-side provider-token verification).
- Mockito test config needs an experimental flag on JDK 24.

**Functional**
- Data is non-persistent (section 3).
- Low-stock is client-side only; no customer cart/orders in the backend (a `CustomerProduct` entity exists but has no controller).
- Large images are stored in the database (5 MB limit) and returned inline as base64 in list responses (slow with many images).
- ESLint has no config in the repo, so `npm run lint` cannot run.
- After login a wholesaler lands on `/products` (not its dashboard) while a retailer lands on the dashboard; cosmetic.
