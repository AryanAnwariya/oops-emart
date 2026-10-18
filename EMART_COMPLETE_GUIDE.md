# E-Mart – Complete Guide (architecture, API, security, interview prep)

> **Read this first.** Every statement below was checked against the repository (file and method names are given so
> you can open them) or against a command that was actually run. Things that do **not** exist are listed in
> [section 12](#12-what-is-not-implemented-say-this-honestly) – state them yourself before an interviewer finds them.
> Test figures come from `mvn clean verify` (91 tests, 0 failures) and `npm run build` (passes) on this branch.

---

## 1. Pitch and walkthrough

### 60-second pitch
E-Mart is a multi-role marketplace I built for my OOPS course. Customers browse a retail catalogue, **retailers**
manage their own shop inventory and **wholesalers** manage a separate wholesale catalogue. The backend is Spring Boot 3
(Java 21) with a layered controller → service → repository design on JPA/Hibernate. The frontend is React 18 with Vite.
The part I'm proudest of is the security model: passwords are BCrypt-hashed, login returns a short-lived signed JWT that
is validated on every request, roles are enforced in Spring Security rather than in React, and every product is tied to
its owner so one seller can never edit another seller's stock. It is covered by 91 automated tests, packaged with a
multi-stage Dockerfile and deployed to Render. It is intentionally *not* a full shop – there is no cart or checkout.

### 3-minute technical walkthrough
1. **(20 s) Shape.** One Spring Boot app serves both the API (`/api/**`) and the built React bundle
   (`Dockerfile` copies `frontend/dist` into `src/main/resources/static`). `HomeController` forwards React routes to
   `index.html`.
2. **(40 s) Auth.** `POST /api/auth/register` → `AuthService.registerUser` validates (email format, 8–72 char password,
   role in {CUSTOMER, RETAILER, WHOLESALER}), hashes with `BCryptPasswordEncoder`, saves the `User` and its role profile in
   one transaction. `POST /api/auth/login` checks the hash and returns `{user…, token, expiresIn}`; `TokenService.issue`
   signs an HS256 JWT (`sub` = user id, `role`, `email`, `iss`, `exp` = 30 min).
3. **(50 s) Every request.** The React helper `apiFetch` (`frontend/src/api.js`) sends `Authorization: Bearer …`.
   Spring's resource-server filter verifies signature, expiry and issuer (`JwtConfig.jwtDecoder`), then
   `JwtUserConverter` reloads the user from the database and rejects the token if the user is gone or the role no longer
   matches. `SecurityConfig.securityFilterChain` then applies role rules (e.g. only `RETAILER` may write to
   `/api/products/**`).
4. **(40 s) Ownership.** Authorization by role is not enough – any retailer could edit another retailer's product. So
   `Product` and `WholesalerProduct` have an `owner_id`; `ProductService.getOwnedProduct` compares it with the id from the
   verified token and throws `AccessDeniedException` (→ HTTP 403). The owner is never read from the request body.
5. **(20 s) Data.** H2 **in-memory** – everything resets on restart. I know that's a limitation; the optional
   `DemoDataSeeder` re-creates demo accounts at boot when `DEMO_SEED=true`.
6. **(10 s) Quality.** 91 tests: token tampering/expiry, role boundaries, cross-owner access, malicious uploads, login
   lockout, validation, seeding safety.

---

## 2. Architecture and request flow

```
Browser (React 18 SPA, Vite)
  │  fetch('/api/…') + Authorization: Bearer <jwt>              frontend/src/api.js  apiFetch()
  ▼
[ dev: Vite proxy :3000 → :8080 ]   [ prod (Docker/Render): same origin, one Spring Boot process ]
  ▼
Tomcat (embedded)
  ▼
Spring Security filter chain                                     config/SecurityConfig
  ├─ CorsFilter                  (localhost origins only; prod is same-origin)
  ├─ BearerTokenAuthenticationFilter  → JwtDecoder (signature, exp, iss)      security/JwtConfig
  │      └─ JwtUserConverter    → reload user, check role in DB                security/JwtUserConverter
  └─ authorizeHttpRequests(...)  role rules per URL                            → 401 / 403 JSON (JsonSecurityErrorHandlers)
  ▼
Controller      (HTTP only: parse, call service, choose status)                controller/*
  ▼
Service         (business rules: validation, ownership, OTP, hashing)          service/*
  ▼
Repository      (Spring Data JPA interfaces, no SQL written)                   repository/*
  ▼
Hibernate → H2 in-memory database (jdbc:h2:mem:emartDB, ddl-auto=update)
```

Example – a retailer edits a product:
`PUT /api/products/update/{id}` (multipart: `product` JSON + optional `imageFile`) →
filter chain: valid JWT, role `RETAILER` ✔ → `ProductController.updateProduct` (principal injected with
`@AuthenticationPrincipal AuthenticatedUser`) → `ProductService.updateProduct`: `validate`, `getOwnedProduct` (404 if
missing, 403 if `ownerId` ≠ caller), `ImageUpload.validate` (if an image was sent) → `ProductRepo.save` → 200.
Errors are translated by `ProductExceptionHandler` (400/403/404) or the security handlers (401/403).

---

## 3. Repository structure

```
backend/src/main/java/com/emart
  EmartApplication.java            Spring Boot entry point
  config/SecurityConfig.java       filter chain, role rules, CORS source, BCrypt bean
  controller/                      AuthController, ProductController, WholesalerProductController, HomeController (SPA forwarding)
  service/                         AuthService, ProductService, WholesalerProductService, EmailService, ImageUpload
  repository/                      UserRepo, CustomerRepo, RetailerRepo, WholesalerRepo, ProductRepo, WholesalerProductRepo, OTPRepo
  model/                           User, Customer, Retailer, Wholesaler, Product, WholesalerProduct, OTP, SocialUser (unused path)
  dto/                             RegisterRequest, LoginRequest, UserResponseDTO, Customer/Retailer/WholesalerDetailsDTO
  security/                        JwtConfig, TokenService, JwtUserConverter, AuthenticatedUser, LoginAttemptService, JsonSecurityErrorHandlers
  exception/                       ResourceNotFoundException, ProductExceptionHandler
  demo/DemoDataSeeder.java         opt-in demo data (off by default)
backend/src/main/resources/application.properties
backend/src/test/java/com/emart    13 test classes (91 tests)
frontend/src
  App.jsx                          routes + catalogue, create, product detail/edit, edit-stock, low-stock pages
  api.js                           session helpers + apiFetch (adds token, handles 401)
  hooks.js                         catalogue() URL builder, useProductList, useRoleGuard, error helpers
  utils.js                         formatPrice, stockStatus, LOW_STOCK_THRESHOLD
  components/                      Navbar, ProductCard (+StockBadge), InventoryDashboard, States (loading/empty/error)
  pages/                           LandingPage, LoginPage (login/register/OTP), Retailer/WholesalerDashboard (thin wrappers)
Dockerfile, README.md, AUTH_MIGRATION.md, DEMO_READINESS.md, EMART_COMPLETE_GUIDE.md
```

Important classes in one line each:
- **`SecurityConfig`** – the single place that says who may call what; also defines the CORS allow-list and the BCrypt bean.
- **`JwtConfig`** – builds the HS256 key from `JWT_SECRET` (refuses to start if missing or < 32 bytes), the encoder and the decoder with issuer validation.
- **`TokenService`** – creates tokens; the clock is injectable so tests can mint expired tokens.
- **`JwtUserConverter`** – turns a verified JWT into an `AuthenticatedUser(id, email, role)` principal after re-checking the database.
- **`AuthService`** – registration rules, BCrypt login, OTP create/verify, email normalisation.
- **`LoginAttemptService`** – in-memory brute-force limiter (5 failures / 10 min per e-mail).
- **`ProductService` / `WholesalerProductService`** – validation, ownership, image handling (near-duplicate classes, see §11).
- **`ImageUpload`** – detects PNG/JPEG/GIF/WebP from the file's first bytes; rejects everything else.
- **`DemoDataSeeder`** – optional, idempotent demo data (only exists when `app.demo.seed=true`).
- **`apiFetch` (frontend)** – adds the Bearer token; on 401 clears the session and sends the user to `/login`.

---

## 4. Data model

```
users (User)                       id, email, password(BCrypt hash), role, location
  ▲ 1                ▲ 1                ▲ 1
  │ @OneToOne        │ @OneToOne        │ @OneToOne        (each profile has user_id → users.id)
customer             retailer           wholesaler
 name, phone,         name, shopName,    name, businessId,
 address              location           contact

product              id(UUID), name, description, category, price(double), quantity(int),
                     imageName, imageType, imageData(@Lob), owner_id  ──► users.id (plain integer, no FK)
wholesaler_product   same columns as product
otp                  id, email, code, expirationTime, failedAttempts
```

- `User` ↔ `Customer`/`Retailer`/`Wholesaler` is **composition by `@OneToOne`**, not inheritance: the account is separate from the role-specific profile.
- `owner_id` is a **plain integer, not a foreign key** – a deliberate shortcut; it means the database does not enforce that the owner exists (the service layer does).
- `Product` and `WholesalerProduct` are **two near-identical classes** (copy-paste). A `@MappedSuperclass` would remove the duplication; I did not refactor it because it works and the risk was not worth it.
- `ownerId` is annotated `@JsonIgnore`, so clients can neither read nor set it.
- Money is stored as `double` – fine for a course project, not for real accounting (`BigDecimal` would be correct).
- **Persistence: H2 in memory.** `spring.datasource.url=jdbc:h2:mem:emartDB…`, `spring.jpa.hibernate.ddl-auto=update`. All users, products, images and OTPs disappear on every restart, redeploy or Render sleep. The PostgreSQL driver is on the classpath but **not configured**. Do not claim persistence.

---

## 5. API reference

Base path `/api`. "Public" = no token. Errors are JSON `{"error": "…"}` for the product and security paths.

| Method & path | Who | Notes |
|---|---|---|
| `POST /auth/register` | public | `email`, `password` (8–72 chars), `role`, optional profile details. 201 + user + `token`. 400 on validation or duplicate e-mail |
| `POST /auth/login` | public | 200 + user + `token`, 401 wrong credentials, **429** after 5 failures (`Retry-After` header) |
| `POST /auth/send-otp?email=` | public | e-mails a 6-digit code (valid 5 min); 500 with a generic message if e-mail fails |
| `POST /auth/verify-otp?email=&code=` | public | existing user → logged in with token; unknown e-mail → `{verified:true}`; code is burned after 5 wrong guesses |
| `POST /auth/social-login` | public | **always 501** (disabled, see §12) |
| `GET /products`, `/products/{id}`, `/products/{id}/image` | public | retail catalogue (204 when empty, 404 unknown id) |
| `GET /products/mine` | RETAILER | only the caller's products |
| `POST /products` | RETAILER | multipart `product` + optional `imageFile`; owner = caller |
| `PUT /products/update/{id}`, `DELETE /products/delete/{id}` | RETAILER, owner only | 403 if not owner, 404 if missing, 400 invalid input |
| `GET /wholesaler/products…`, `/wholesaler/products/mine` and writes | same pattern; writes and `/mine` need WHOLESALER | wholesale catalogue |
| `GET /actuator/health` | public | every other `/actuator/**` is denied |
| any other `/api/**` | authenticated | secure by default |

Rules live in `SecurityConfig.securityFilterChain`; the order of matchers matters (`/mine` is matched before the public `GET` rules).

---

## 6. Authentication and authorization

### Registration → login → token
1. **Register** (`AuthService.registerUser`, `@Transactional`): normalise e-mail (trim, lower-case), validate format, require 8–72 character password (BCrypt only uses the first 72 bytes, so longer ones are refused), allow only the three roles, reject duplicates, hash with `BCryptPasswordEncoder` (default cost 10), save `User`, then the matching profile entity.
2. **Login** (`AuthController.login`): check `LoginAttemptService` (429 if locked) → `AuthService.login` compares with `passwordEncoder.matches` → on success reset the counter and return `UserResponseDTO` (no password) plus a token; on failure record it and return 401.
3. **Token** (`TokenService.issue`): HS256 JWT, claims `iss` (default `emart`), `sub` (user id), `iat`, `exp` (default 30 min, `JWT_TTL`), `jti`, `role`, `email`. The signing secret comes only from the `JWT_SECRET` environment variable; the app refuses to start without ≥ 32 bytes (`JwtConfig.jwtSecretKey`).

### Every protected request
`BearerTokenAuthenticationFilter` → `JwtDecoder` (signature, `exp`, `iss`) → `JwtUserConverter.convert`:
subject must be a number, role must be one of the three, the user must still exist **and** the role in the token must equal the role in the database, otherwise 401. Authorities are built from the **database** role (`ROLE_RETAILER`…), never from client-supplied data. Then `authorizeHttpRequests` applies the URL rules (403 if the role is wrong).

### What stops common attacks (each has a test)
| Attack | Defence | Test |
|---|---|---|
| Call write APIs without logging in | 401 from the filter chain | `SecurityAccessTest` |
| Customer/other role calls seller APIs | 403 by role | `SecurityAccessTest` |
| Edit/delete another seller's product | owner check → 403; client `id`/`ownerId` ignored | `ProductOwnershipTest` |
| Forged / expired / wrong-issuer / `alg:none` / tampered token | decoder rejects | `TokenServiceTest`, `SecurityAccessTest` |
| Token for a deleted user or changed role | `JwtUserConverter` DB check → 401 | `SecurityAccessTest` |
| Password guessing | 5 failures → 429 for 10 min | `LoginAttemptServiceTest`, `AuthHardeningTest` |
| OTP guessing | code burned after 5 wrong tries | `AuthTokenIssuanceTest` |
| HTML/SVG uploaded as an "image" (stored XSS) | type detected from bytes; HTML/SVG rejected; name stripped of paths; `nosniff` header | `UploadAndInputValidationTest` |
| Oversized fields → server error | 400 with a message | `UploadAndInputValidationTest` |
| Cross-origin calls from other sites | CORS allow-list is localhost only | `AuthHardeningTest` |

### Why these choices
- **CSRF is disabled** because the API is stateless and uses a bearer header, not cookies.
- **JWT in `localStorage`** is simple but readable by any XSS – that is the trade-off; the upload hardening and the absence of `dangerouslySetInnerHTML` (0 uses in `frontend/src`) reduce XSS risk, but no Content-Security-Policy header is configured.
- The **database re-check** on each request costs one query but makes role changes and user deletion take effect immediately, which pure stateless JWTs cannot do.
- **Roles in React are cosmetic.** `Navbar`/`useRoleGuard` only hide links; the server decides.

### Social login (Google/Facebook)
Disabled on purpose. The old endpoint trusted an e-mail sent by the browser, which would allow logging in as anyone. `AuthController.socialLogin` now returns 501 and the UI hides the buttons unless `VITE_ENABLE_*_LOGIN=true`. Re-enabling requires verifying the provider's ID token on the server.

---

## 7. Product ownership and stock

- **Create:** `ProductService.addProduct` sets `id = null` (otherwise `save()` could overwrite someone else's row if the client sent an existing id) and `ownerId = principal.id`.
- **Update/delete:** `getOwnedProduct` → 404 if absent, `AccessDeniedException` (→ 403 via `ProductExceptionHandler`) if `callerId` ≠ `ownerId`. Products with `ownerId == null` (legacy rows from before this feature) can be read but **not** modified by anyone (fail closed; assign owners with SQL, see `AUTH_MIGRATION.md`).
- **Reads:** the catalogue is public. `GET …/mine` returns only the caller's products.
- **Stock:** `quantity` is a plain integer edited by the owner (create/update). "Low stock" means `quantity < 10` and is computed **in the browser** (`utils.js` `LOW_STOCK_THRESHOLD`, used by `InventoryDashboard`, the Low stock page and the badges). There is no backend low-stock endpoint, no stock reservation, and no concurrency control: two simultaneous edits are last-write-wins (no `@Version`). That is acceptable only because stock changes solely by owner edits – there is no ordering.
- **Images:** stored in the database (`@Lob byte[]`), 5 MB limit (`spring.servlet.multipart`), served by `GET …/{id}/image` with the detected type. List responses also contain the image bytes as base64, which is wasteful with many images.

---

## 8. Frontend notes

- **Routes** (`App.jsx`): `/`, `/products`, `/login`, `/retailer/dashboard`, `/retailer/low-stock`, `/wholesaler/dashboard`, `/create`, `/edit`, `/product/:id`, `/wholesaler/product/:id`, `*` (404 page). `HomeController` forwards each of these to `index.html` on direct visits (covered by `SpaForwardingTest`).
- **Session:** `saveSession` stores the user (without token) and the token separately in `localStorage`; `clearSession` on logout/401.
- **Loading / empty / error / success** states are shared components in `components/States.jsx`.
- **Role-aware navigation:** `Navbar.linksFor(role)`.
- **Catalogue scope:** a wholesaler browsing `/products` sees the wholesale catalogue; everyone else sees retail (`ProductsPage`).
- There are **no frontend unit tests**; ESLint has no config file in the repo, so `npm run lint` cannot run.

---

## 9. Run, test, build, deploy

```bash
# Backend (needs JDK 21+, Maven 3.9+)
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"     # required, >= 32 bytes
mvn clean verify                                    # 91 tests
java -jar target/backend-1.0.0.jar                  # http://localhost:8080

# Frontend (Node 18+)
cd frontend && npm ci && npm run dev                # http://localhost:3000 (proxies /api to :8080)
npm run build                                       # production bundle in dist/
```
On JDK 24 the pom passes `-Dnet.bytebuddy.experimental=true` to the test JVM (Boot 3.2's Mockito predates JDK 24).

**Docker** (`Dockerfile`, 3 stages): Node builds the React app → Maven builds the jar with the bundle in `static/` (`-DskipTests`) → JRE 21 runs `java -Xmx400m -Dserver.port=${PORT} -jar app.jar`. The Docker build itself has **not** been run on the author's machine (Docker not installed there); Render builds it.

**Render** (web service built from the Dockerfile, auto-deploy from `main`): set `JWT_SECRET` (required), optionally `MAIL_USERNAME`/`MAIL_PASSWORD` for real OTP e-mails, and for a presentation `DEMO_SEED=true` + `DEMO_PASSWORD` (≥ 12 chars). See `README.md` → *Seeded demo data*.

| Variable | Needed | Purpose |
|---|---|---|
| `JWT_SECRET` | yes | signing key |
| `JWT_ISSUER`, `JWT_TTL` | no | defaults `emart`, `PT30M` |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | for OTP e-mail | Gmail SMTP app password |
| `OTP_LOG_CODES` | no | local demos only: prints OTP to the console if e-mail fails |
| `DEMO_SEED`, `DEMO_PASSWORD` | no | opt-in demo accounts/products |
| `H2_CONSOLE_ENABLED` | no | H2 web console (keep off) |
| `PORT` | set by Render | HTTP port |

**Deployment caveats:** free instances sleep; every restart empties the in-memory database (the seeder re-creates demo data if enabled). A Gmail app password that was committed early in the project is still in **git history** and must be revoked in the Google account.

---

## 10. Testing (what is actually covered)

`mvn clean verify` → **91 tests, 0 failures** (13 classes):

| Class | Tests | Focus |
|---|---|---|
| `AuthServiceTest` | 16 | registration rules, hashing, login, OTP logic |
| `AuthControllerTest` | 8 | status codes, no internal-error leakage, 429 mapping |
| `AuthTokenIssuanceTest` | 6 | register/login/OTP return working tokens; social login issues none; OTP lockout |
| `AuthHardeningTest` | 5 | login lockout, registration rules, CORS |
| `SecurityAccessTest` | 12 | 401/403 matrix, bad/expired/forged tokens, role mismatch |
| `TokenServiceTest` | 7 | claims, expiry, wrong key/issuer, tampering, `alg:none`, weak secret |
| `ProductOwnershipTest` | 10 | owner-only edit/delete, id/owner spoofing, 404s, validation, images |
| `UploadAndInputValidationTest` | 7 | disguised HTML/SVG, detected image type, field limits |
| `LoginAttemptServiceTest` | 6 | limiter window, reset, bounded memory |
| `DemoDataSeederTest` / `…SafetyTest` / `…DisabledByDefaultTest` | 6 / 5 / 1 | seeding, idempotency, never touching existing accounts, off by default |
| `SpaForwardingTest` | 2 | React routes survive a page refresh |

Most tests use `@SpringBootTest` + MockMvc against the real filter chain and an in-memory database, so they exercise real security, not mocks. **Not tested:** frontend components, the Docker build, live Render behaviour, e-mail delivery, concurrent edits.

---

## 11. Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| Stateless JWT + DB re-check per request | scales without server sessions, but role changes/deletions apply immediately | one extra query per request |
| One deployable (Spring serves React) | no CORS in production, one thing to deploy | frontend and backend release together |
| Entities returned directly for products | less code | image bytes in list JSON; entity shape = API shape |
| Separate `Product` / `WholesalerProduct` | the two catalogues are independent | duplicated classes, services and controllers |
| H2 in memory | zero setup for a course project | **no persistence** |
| In-memory login limiter | no new infrastructure | resets on restart; per instance; lets an attacker lock a known account for 10 min |
| Roles checked in `SecurityConfig` URL rules + ownership in services | two layers: *who may call* and *whose data* | rules are spread over two places |
| Client-side low-stock filter | tiny dataset, trivial | not scalable; no server endpoint |
| `double` for prices | simplicity | not suitable for real money |
| Mixed DI style | older classes use field `@Autowired`, newer ones constructor injection | inconsistent; constructor injection is the better practice |

---

## 12. What is NOT implemented (say this honestly)

- **No cart, checkout, orders or payments.** E-Mart is a catalogue and inventory tool. (A cart/checkout design was started and deliberately dropped to keep scope small; none of it is in the repository.)
- **No persistent database** (H2 in memory); no migrations tool (Flyway/Liquibase).
- **Social login disabled**; no password reset; no refresh tokens or server-side logout (a token stays valid until it expires, 30 min).
- **Registration does not require e-mail verification** – the OTP flow can log an existing user in and returns `verified:true` for unknown e-mails, but `register` does not enforce it.
- **Rate limiting only on login** (not registration or OTP sending).
- **No admin role**, no audit log, no pagination (lists return everything), search/sort/filter are client-side.
- **No frontend tests**, no ESLint config, no CI pipeline.
- **No concurrency control on stock edits.**
- No Content-Security-Policy header.

### Future improvements (in the order I would do them)
1. Persistent PostgreSQL via environment variables + Flyway (the driver is already a dependency).
2. Refactor `Product`/`WholesalerProduct` into a `@MappedSuperclass` and share one service.
3. Product DTOs and image-by-URL only (stop sending base64 in lists), pagination.
4. Optimistic locking (`@Version`) on products; `BigDecimal` prices.
5. HttpOnly-cookie or short-lived access + refresh tokens; CSP header.
6. Server-side Google/Facebook ID-token verification to re-enable social login.
7. Orders/checkout with transactional stock deduction and pessimistic locking.
8. GitHub Actions CI running `mvn verify` and `npm run build`.

---

## 13. Live demonstration checklist

**Before** (10 min early): open the Render URL once to wake it; confirm `/products` loads. With demo mode on (`DEMO_SEED=true`) the four demo accounts and 9 sample products exist; otherwise register one account per role. Remember a restart wipes everything.

1. Landing page → *Browse products*: search "rice", filter category, sort by price. Point out the stock badges.
2. Sign in as **customer**: Products only in the navbar; open a product (read-only, no edit buttons).
3. Sign in as **retailer**: dashboard totals (products, units, low stock). Add a product with an image. Edit quantity to 3 → low-stock badge and the *Low stock* page.
4. As **retailer 2**, open retailer 1's product and press *Edit* → *Save*: **"You do not have permission"** (403 from the server). Show the Network tab.
5. As **wholesaler**: separate catalogue and dashboard.
6. Security moments (browser console or curl): call `POST /api/products` with no token → 401; with a customer token → 403; five wrong logins → 429.
7. Show `mvn clean verify` output (91 tests) and the `SecurityConfig` rules.
8. Mention limitations first: in-memory database, no checkout.

---

## 14. CV entry

**E-Mart – Multi-role marketplace (React · Spring Boot · JWT)** — course project, Java 21 / Spring Boot 3.2 / React 18

- Built a full-stack marketplace with customer, retailer and wholesaler roles: a layered Spring Boot REST API (controller → service → repository, JPA/Hibernate with one-to-one role profiles) and a responsive React 18/Vite SPA with role-aware navigation and an inventory dashboard with low-stock alerts; packaged with a multi-stage Docker build and deployed to Render.
- Designed stateless security: BCrypt password hashing, short-lived signed JWTs validated on every request (signature, expiry, issuer, plus a database re-check of user and role), URL-level role rules in Spring Security, server-side per-product ownership checks that return 403 to non-owners, login throttling (HTTP 429), and image-upload validation that blocks HTML/SVG disguised as images.
- Wrote 91 automated JUnit 5 / Mockito / MockMvc tests (all passing) covering token tampering and expiry, role and ownership boundaries, upload abuse, validation and an idempotent, opt-in demo-data seeder.

**Technologies:** Java 21, Spring Boot 3.2, Spring Security (OAuth2 resource server/JWT), Spring Data JPA, Hibernate, H2, Maven, JUnit 5, Mockito, React 18, React Router, Vite, Docker, Render, Git.

*Only the 91-test figure is a measurement (from `mvn clean verify`). No users, traffic or performance numbers are claimed because none exist.*

---

## 15. Interview questions and truthful answers

**1. Walk me through what happens when a user logs in.**
The React form posts to `/api/auth/login`. `AuthController.login` first asks `LoginAttemptService` whether that e-mail is locked (429 if so), then `AuthService.login` normalises the e-mail, loads the user and uses `BCryptPasswordEncoder.matches`. On success it returns the user (via `UserResponseDTO`, so no password) plus a JWT from `TokenService.issue`. The frontend `saveSession` stores the token in `localStorage` and `apiFetch` sends it on later requests.

**2. Why JWT instead of server sessions?**
The API is stateless, which keeps the server simple and works with one jar. The downside is revocation: a JWT stays valid until `exp`. I reduced that by making tokens short (30 min) and by re-checking the user and role in the database on every request (`JwtUserConverter`), so deleted users and role changes take effect immediately. There is no server-side logout.

**3. How are passwords stored?**
BCrypt via Spring's `BCryptPasswordEncoder` (default cost 10) in `SecurityConfig.passwordEncoder`; the salt is part of the hash. Plain passwords are never stored. The demo seeder's password is not printed either: a test checks the console output and I confirmed the application log contained no occurrence of it. Passwords are limited to 8–72 characters because BCrypt ignores bytes after 72.

**4. What is inside your token and how is it verified?**
HS256 with claims `iss`, `sub` (user id), `iat`, `exp`, `jti`, `role`, `email`. The key comes from `JWT_SECRET`; startup fails if it is shorter than 32 bytes. `JwtConfig.jwtDecoder` verifies signature, expiry and issuer; `JwtUserConverter` additionally checks the user still exists and the role matches the database. Tests cover tampered payloads, wrong key, wrong issuer, expired tokens and `alg:none`.

**5. Authentication vs authorization – where is each in your code?**
Authentication = proving identity (login + JWT verification). Authorization = what you may do. Role authorization is in `SecurityConfig.securityFilterChain` (e.g. `/api/products/**` writes need `RETAILER`). Data-level authorization (ownership) is in `ProductService.getOwnedProduct`.

**6. Why do you need ownership checks if you already have roles?**
Roles say *a retailer can edit products*; they don't say *which* products. Without an owner check any retailer could edit any other retailer's stock (an IDOR flaw). `owner_id` is set from the verified token on create and compared on update/delete; non-owners get 403. It's tested in `ProductOwnershipTest.otherRetailer_cannotUpdateOrDelete`.

**7. Could a client set the owner of a product?**
No: `Product.ownerId` is `@JsonIgnore`, so it is neither read from nor written to JSON, and the service assigns it from the principal. The client-supplied `id` is also nulled on create, because `save()` with an existing id would overwrite another seller's row – there's a test for exactly that.

**8. What happens to products created before ownership existed?**
They have `owner_id = NULL`. They stay publicly readable but nobody can modify them (fail closed) until an owner is assigned with SQL – documented in `AUTH_MIGRATION.md`.

**9. How do you prevent brute-force logins?**
`LoginAttemptService`: 5 failed attempts per e-mail within 10 minutes → HTTP 429 with `Retry-After`; a successful login resets it. Limitations: in memory (lost on restart, per instance) and keyed by e-mail, so an attacker can deliberately lock a known account for 10 minutes. It only covers login, not registration or OTP sending.

**10. What is the biggest security bug you found and fixed?**
Two. First, originally every API endpoint was `permitAll()` and the product endpoints trusted the client – anyone could create or delete products. Second, uploaded "images" were served with the client-declared content type, so an HTML file uploaded as an image would execute on the app's origin and could read the JWT from `localStorage`. `ImageUpload` now detects the type from the bytes, accepts only PNG/JPEG/GIF/WebP and strips path parts from the file name; `X-Content-Type-Options: nosniff` is sent.

**11. Why did you disable social login?**
The original endpoint accepted an e-mail from the browser without verifying a Google/Facebook token, so anyone could log in as any registered user. It now returns 501 (`AuthController.socialLogin`) and the UI hides the buttons. Re-enabling needs server-side ID-token verification.

**12. Is CSRF a concern?**
Not for this design: authentication is a bearer token in a header, not a cookie, so browsers don't attach it automatically. CSRF protection is therefore disabled in `SecurityConfig`. If I moved the token to a cookie I'd have to enable it.

**13. Where do you store the token and what is the risk?**
`localStorage`. Any XSS could read it. Mitigations: React escapes output (no `dangerouslySetInnerHTML` anywhere), uploads can't be HTML/SVG, tokens are short-lived. Not done: a CSP header, HttpOnly cookies.

**14. Explain your CORS setup.**
`SecurityConfig.corsConfigurationSource` allows only `localhost:3000/5173/4173` for local development. In production the SPA and API share an origin, so no CORS is needed. A test verifies that a foreign origin gets 403. I removed a duplicate `WebConfig` and the controllers' `@CrossOrigin` annotations that overlapped with it.

**15. How is the project structured and why?**
Layers: controller (HTTP), service (rules), repository (persistence), model/dto. Controllers don't contain business logic; services don't know about HTTP. Security is a cross-cutting filter chain. It makes each layer testable on its own, e.g. `AuthServiceTest` uses mocks while `SecurityAccessTest` runs the whole chain.

**16. Do you use DTOs? Why or why not?**
For authentication yes (`RegisterRequest`, `LoginRequest`, `UserResponseDTO` so the password hash is never serialised). For products I return the entity directly, which is a shortcut: it leaks the image bytes into list responses and ties the API shape to the table. Proper product DTOs are on my improvement list.

**17. Which OOP concepts does the code really use?**
Encapsulation (private fields + getters, `AuthenticatedUser` record, immutable-ish `TokenService`), abstraction via Spring Data repository interfaces and the service layer, composition (`Retailer` *has a* `User`), polymorphism through interfaces (`JpaRepository`, `Converter<Jwt, …>` implemented by `JwtUserConverter`, `SmartInitializingSingleton` in the seeder) and dependency injection. I do **not** use entity inheritance – and the duplicated `Product`/`WholesalerProduct` shows where it would help.

**18. Why `@OneToOne` between `User` and the role profiles instead of inheritance?**
Composition keeps authentication data separate from role details and avoids inheritance mapping complexity (single-table vs joined). The cost is an extra join/query when profile data is needed.

**19. What does `@Transactional` do in your code and where?**
`AuthService.registerUser` is transactional so the user and its role profile are saved atomically (no user without a profile). `verifyOtp` is transactional so deleting used codes and the check happen together. Product operations are single repository calls (each already transactional through Spring Data), so I didn't add more.

**20. How do you handle errors consistently?**
Partly. Security failures return JSON 401/403 via `JsonSecurityErrorHandlers`. Product errors go through `ProductExceptionHandler` (`ResourceNotFoundException`→404, `AccessDeniedException`→403, `IllegalArgumentException`→400). `AuthController` still uses its own try/catch, but now returns validation messages only for `IllegalArgumentException` and a generic message for anything else (test `unexpectedFailures_returnAGenericMessage…`). A single `@RestControllerAdvice` for everything would be cleaner.

**21. How does stock management work, and what can go wrong?**
`quantity` is an integer the owner edits; the UI flags `< 10` as low stock (computed in the browser). It can go wrong with concurrent edits: there is no `@Version`, so the last write wins. That's tolerable because only owners change stock, but it's why a real checkout would need locking.

**22. Why no checkout/cart?**
Scope. I wanted a small system I fully understand and can secure. I started designing checkout (orders with price snapshots, stock deduction under row locks) but dropped it deliberately; nothing of it is in the repo. I'd describe how I'd build it: `Order`/`OrderItem` with copied prices, a transaction that locks product rows in a fixed order and rolls back on insufficient stock.

**23. Your database is in memory. Isn't that a serious flaw?**
For production, yes – every restart wipes data. It was a conscious course-project simplification and I document it. The mitigation for demos is the idempotent `DemoDataSeeder`. The migration path is PostgreSQL through env vars (driver already present) plus Flyway.

**24. How does the demo seeder avoid being dangerous in production?**
It only exists when `app.demo.seed=true`, requires `DEMO_PASSWORD` ≥ 12 chars (never committed or logged), runs before the web server accepts traffic (so nobody can register a demo e-mail first), never modifies an existing account (warns and skips it), and is idempotent. Tests: `DemoDataSeederSafetyTest`, `DemoDataDisabledByDefaultTest`.

**25. How did you test security, not just functionality?**
Through the real filter chain with MockMvc: missing/garbage/expired/forged tokens, deleted user, role mismatch, wrong-role calls, cross-owner edits, spoofed ids/owners, disguised uploads, brute force, CORS. Each defence in §6 maps to a named test.

**26. How would you scale this?**
Move to a real database, make the app stateless-friendly (move the login limiter and OTP state to Redis or the database), store images in object storage, add pagination and caching, and run several instances behind a load balancer. The JWT design already works across instances provided they share `JWT_SECRET`.

**27. What would you change first if you had one more week?**
Persistent PostgreSQL + Flyway, then product DTOs/pagination, then the `@MappedSuperclass` refactor, then CI. Persistence first because it is the largest correctness gap.

**28. How do you deploy?**
Render builds the multi-stage `Dockerfile` (Node → Maven → JRE 21). `main` auto-deploys. Required: `JWT_SECRET`; optional mail and demo variables. I can't see Render's build logs from my tooling, so I verify a deploy by loading the site and checking the log for `Started EmartApplication`.

**29. What bug did you find only because you tested in a browser?**
Several: the wholesaler product page was a blank screen (the dashboard linked to a route that didn't exist and the detail page always called retail endpoints), the *Edit stock* form couldn't accept UUID ids (it was a number input), and – found later by comparing the server's SPA forwarding list with the React routes – refreshing `/edit` or a product page on the deployed site returned 404. Now covered by `SpaForwardingTest`.

**30. What's the difference between 401 and 403 in your API?**
401 = not authenticated (missing/invalid/expired token or deleted user). 403 = authenticated but not allowed (wrong role or not the owner). Both return JSON `{error}`.

**31. Why return 404 vs 403 for missing vs foreign products?**
404 when the id doesn't exist, 403 when it exists but belongs to someone else. That reveals existence of ids, but the catalogue is public anyway, so nothing sensitive leaks. For private data (like orders) I'd return 404 for both.

**32. How do you validate input?**
Server-side, in the services: e-mail format and password length at registration; product name required (≤ 100), description ≤ 255, category ≤ 100, price non-negative and finite, quantity non-negative; uploads checked by content. The React forms validate the same rules for fast feedback, but the server is authoritative. I use hand-written checks rather than Bean Validation annotations – a possible cleanup.

**33. How does the OTP flow work and what are its weaknesses?**
`sendOtp` creates a 6-digit code with `SecureRandom`, stores it with a 5-minute expiry and e-mails it. `verifyOtp` compares it, burns it after use or after 5 wrong guesses, and for an existing user returns a token (a passwordless login). Weaknesses: no rate limit on *sending* codes, codes are stored in plain text, and registration doesn't require a verified e-mail.

**34. Why did you remove `CustomerProduct`, `WebConfig` and `application.yml`?**
They were dead or duplicate: no code referenced `CustomerProduct`, `WebConfig` duplicated the CORS config in `SecurityConfig`, and `application.yml` only repeated settings already in `application.properties`. I searched for references first and the full test suite plus a jar start-up check confirmed nothing depended on them.

**35. What would you say is *not* production-ready?**
In-memory data, no refresh/revocation, `localStorage` token, no CSP, login-only rate limiting, double prices, duplicated product classes, no frontend tests and no CI. I can list these because I wrote them down in §12 rather than hoping nobody asks.

**36. What did you learn?**
That "it works in the browser" is not security: the original app hid buttons in React but the API was open. Real protection is on the server, verified by tests that try to break it.

---

## 16. Quick reference – commands that were run for this guide
- `cd backend && mvn clean verify` → 91 tests, 0 failures, BUILD SUCCESS.
- `cd frontend && npm run build` → success.
- Packaged jar run locally (no Docker): weak password → 400; bad e-mail → 400; HTML uploaded as `image/png` → 400; real PNG declared `text/html` → 201 and served as `image/png` with `nosniff`; 150-char name → 400; five wrong logins then the correct one → 429; foreign-origin CORS preflight → 403.
- Not run: Docker build, Render deployment of this branch, frontend automated tests (none exist).
