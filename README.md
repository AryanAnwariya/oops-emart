# E-Mart

A multi-role marketplace built for an Object-Oriented Programming course project (CSF213). **Customers** browse products, **retailers** manage a retail catalogue, and **wholesalers** manage a separate wholesale catalogue. Spring Boot REST API + React single-page app, served together from one Docker image.

> Course project, not a production system. See [Known limitations](#known-limitations) and [DEMO_READINESS.md](DEMO_READINESS.md).

## Tech stack
| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.2 (Web, Data JPA, Security, OAuth2 Resource Server, Validation, Mail, Actuator) |
| Auth | BCrypt password hashing, short-lived signed JWT (HS256), email OTP |
| Database | H2 **in-memory** by default (PostgreSQL driver included but not wired up) |
| Frontend | React 18, React Router 6, Vite 5, plain `fetch` |
| Tests | JUnit 5, Mockito, Spring MockMvc (66 tests) |
| Deploy | Multi-stage Dockerfile (Node build -> Maven build -> JRE), Render |

## Architecture
```
React SPA (src/App.jsx, pages/*)
   │  fetch + "Authorization: Bearer <token>"      (src/api.js)
   ▼
Spring Security filter chain      config/SecurityConfig, security/*   -> 401 / 403, role rules
   ▼
Controllers   (HTTP only)         controller/*   Auth, Product, WholesalerProduct, Home (SPA forwarding)
   ▼
Services      (business rules)    service/*      AuthService, ProductService, WholesalerProductService, EmailService
   ▼
Repositories  (Spring Data JPA)   repository/*
   ▼
Entities / DTOs                   model/*, dto/*   -> H2
```
The built React app is copied into `src/main/resources/static`, so one Spring Boot process serves both the UI and `/api`.

## Run it locally
Requirements: JDK 21+, Maven 3.9+, Node 18+. No database to install (H2 in memory).

```bash
# 1) Backend (port 8080). JWT_SECRET is mandatory: >= 32 random bytes.
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
export OTP_LOG_CODES=true          # local demo only: prints OTP codes to the console if SMTP is not configured
mvn clean package
java -jar target/backend-1.0.0.jar

# 2) Frontend dev server (port 3000, proxies /api to :8080)
cd frontend
npm ci
npm run dev
```
Open http://localhost:3000 . Run the tests with `cd backend && mvn clean verify` and build the UI with `cd frontend && npm run build`.
(On JDK 24 the pom passes `-Dnet.bytebuddy.experimental=true` to the test JVM because Boot 3.2's Mockito predates JDK 24.)

### Environment variables
| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | yes | token signing key (app will not start without it) |
| `JWT_ISSUER`, `JWT_TTL` | no | default `emart`, `PT30M` |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | for real OTP emails | Gmail SMTP + app password |
| `OTP_LOG_CODES` | no (`false`) | local-demo only |
| `DEMO_SEED`, `DEMO_PASSWORD` | no (off) | opt-in demo accounts/products for presentations (local or Render); see "Seeded demo data" |
| `PORT` | no (`8080`) | HTTP port |
| `H2_CONSOLE_ENABLED` | no (`false`) | H2 web console |
| `VITE_ENABLE_GOOGLE_LOGIN` / `VITE_ENABLE_FACEBOOK_LOGIN` | no | build-time; social login is off unless `true` (and the server still rejects it) |

### Docker
```bash
docker build -t emart .
docker run -p 8080:8080 -e JWT_SECRET="$(openssl rand -base64 48)" emart
```
(The Dockerfile has not been built in the author's environment; Docker was unavailable.)

## Demo script (email/password path)
1. Register a **Retailer** (Create Account -> role Retailer) -> lands on the Retailer Dashboard.
2. *Add New Product* (name, price, quantity, optional image) -> appears on the dashboard.
3. Open the product -> *Edit* / *Delete*; or *Edit Stock* page -> click a row to edit price/quantity.
4. Set a quantity below 10 -> the **Refill Stock** badge counts it and the *Low on Stock* page lists it (filter runs in the browser over `GET /api/products/mine`).
5. Register a **Customer** -> *Products* page shows the retailer's catalogue (public read).
6. Register a **Wholesaler** -> separate catalogue and dashboard (`/api/wholesaler/products`).
7. Security moment: as a second retailer open the first retailer's product and press Save -> "You do not have permission".

## Seeded demo data (local and Render presentations)
Optional and **off by default**. When `DEMO_SEED=true` the app creates demo accounts and products during startup, **before the web server accepts requests**, using the normal services (validation, BCrypt hashing, ownership). Rules enforced in code and tests:
- `DEMO_PASSWORD` (at least **12** characters) is required, is **never stored in this repository**, and is never logged. Without it the app refuses to start in demo mode.
- It only creates. An existing account is never modified or re-passworded; if a demo email already exists with a different role or password, it is left untouched, a warning is logged, and no demo products are attached to it.
- It is idempotent (accounts, and products per owner and name, are created only if missing), so a restart never duplicates data.
- With the in-memory H2 database every start is empty, so the seeder recreates the demo data on each boot. **Anything created during the presentation is lost on restart**, and the demo data exists only while `DEMO_SEED=true`.

| Role | Email | Products |
|---|---|---|
| Customer | `demo.customer@demo.emart.test` | browses everything |
| Retailer 1 | `demo.retailer1@demo.emart.test` | Alphonso Mango (40), Amul Butter (25), Basmati Rice 1kg (**8, low**), Toor Dal 1kg (**3, low**) |
| Retailer 2 | `demo.retailer2@demo.emart.test` | Green Tea (15), A4 Notebook (**5, low**) |
| Wholesaler | `demo.wholesaler@demo.emart.test` | Rice 25kg (200), Cooking Oil 15L (40), Sugar 50kg (**6, low**) |

```bash
cd backend && mvn clean package -DskipTests
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_SEED=true DEMO_PASSWORD="choose-a-local-only-password"
java -jar target/backend-1.0.0.jar          # then: cd ../frontend && npm run dev
```
### Render (presentation setup)
In the Render dashboard -> your web service -> **Environment**, add:

| Variable | Value | Notes |
|---|---|---|
| `JWT_SECRET` | output of `openssl rand -base64 48` | required for the app to start at all |
| `DEMO_SEED` | `true` | the switch; must be exactly `true` |
| `DEMO_PASSWORD` | a strong password you invent (>= 12 chars) | mark as secret; the same for all four accounts; do not reuse a real password |

Save (Render redeploys). Wait for the deploy, open https://oops-emart.onrender.com and sign in with the emails above and your `DEMO_PASSWORD`. Check the service log for `DEMO MODE IS ON` and `Demo data ready` (the password is never printed).
Free instances sleep when idle and the first request wakes them; the wake-up is a restart, and the seeder recreates the demo data automatically (open the site a few minutes before presenting).

**Teardown after the presentation:** delete `DEMO_SEED` and `DEMO_PASSWORD` (or set `DEMO_SEED=false`) and let Render redeploy. Because the database is in memory, the demo accounts and products disappear with the restart; nothing remains to clean up. Optionally rotate `JWT_SECRET` to invalidate every issued token.

**Risks while demo mode is on:** the four emails are public (they are in this README) and anyone who learns the password can act as those users; there is no login rate limiting. Use a long unique password, and keep demo mode on only as long as needed.

Local sign-in: http://localhost:3000 with any email above and your `DEMO_PASSWORD`. Retailer 1's *Refill Stock* badge shows 2; log in as Retailer 2 and try to edit Retailer 1's product to show the 403 ownership check.

## API routes
Base `/api`. "Public" needs no token; otherwise `Authorization: Bearer <token>`.

| Method & path | Access | Notes |
|---|---|---|
| `POST /auth/register` | public | JSON: email, password, role (`CUSTOMER`/`RETAILER`/`WHOLESALER`), location, plus `customerDetails` / `retailerDetails` / `wholesalerDetails`. Returns user + `token`, `expiresIn` |
| `POST /auth/login` | public | email, password -> user + token (401 on bad credentials) |
| `POST /auth/send-otp?email=` | public | emails a 6-digit code (5 min) |
| `POST /auth/verify-otp?email=&code=` | public | existing user -> logged in (token); new email -> `{verified:true}`; code burned after 5 wrong guesses |
| `POST /auth/social-login` | public | **disabled**, always 501 |
| `GET /products`, `GET /products/{id}`, `GET /products/{id}/image` | public | retail catalogue |
| `GET /products/mine` | RETAILER | only the caller's products |
| `POST /products` (multipart: `product` JSON + optional `imageFile`) | RETAILER | owner = caller |
| `PUT /products/update/{id}`, `DELETE /products/delete/{id}` | RETAILER, owner only | 403 if not owner, 404 if missing |
| `GET/POST/PUT/DELETE /wholesaler/products/...` | same pattern; writes and `/mine` need WHOLESALER | wholesale catalogue |
| `GET /actuator/health` | public | other actuator endpoints are denied |

## Database design
Entities (JPA, `ddl-auto=update`):
- **User** (`users`): id, email (unique in practice, stored lower-case), BCrypt `password`, `role`, location.
- **Customer / Retailer / Wholesaler**: role-specific profile, each with a `@OneToOne` -> `User` (`user_id`). Customer: name, phone, address. Retailer: name, shopName, location. Wholesaler: name, businessId, contact.
- **Product** (retail) and **WholesalerProduct**: UUID id, name, description, category, price, quantity, image (name, type, `@Lob` bytes), `owner_id` (user id of the owner; never exposed in JSON).
- **CustomerProduct**: entity exists but has no API (unused).
- **OTP**: email, code, expiry, failed attempts.

There is no foreign key from `owner_id` to `users` (it is a plain integer); there are no order/cart tables.

## Object-oriented design (what is really in the code)
- **Encapsulation:** entities and DTOs keep fields `private` behind getters/setters; `UserResponseDTO` exists so the password hash is never serialised; `Product.ownerId` is `@JsonIgnore` so clients cannot read or set it.
- **Abstraction / layering:** controller -> service -> repository. Controllers only translate HTTP; business rules (validation, ownership, OTP limits) live in services; persistence is hidden behind Spring Data interfaces (`ProductRepo extends JpaRepository<Product,String>` – no SQL written).
- **Composition:** a `Retailer` *has a* `User` (`@OneToOne`) instead of subclassing it; profile data is composed with the account.
- **Polymorphism (interface-based):** Spring injects interface implementations (`JpaRepository`, `WebMvcConfigurer`, `Converter<Jwt, AbstractAuthenticationToken>` implemented by `JwtUserConverter`); `AuthenticatedUser` is a Java `record`.
- **Dependency injection / inversion of control:** services and controllers get collaborators via Spring (`@Autowired`, constructor injection in `TokenService`/`JwtUserConverter`).
- **Exceptions as control flow for errors:** `ResourceNotFoundException` -> 404 via `ProductExceptionHandler`; Spring's `AccessDeniedException` -> 403.
- **Honest gaps:** there is **no entity inheritance** – `Product`, `WholesalerProduct` and `CustomerProduct` are near-identical classes, and the two product services/controllers are copies. A `@MappedSuperclass` base class plus a generic service would remove the duplication (listed as the main refactoring).

## Security model
Passwords are BCrypt-hashed. Login returns a 30-minute HS256 JWT (`sub` = user id, `role`, `iss`, `exp`). On every protected request Spring Security verifies signature, expiry and issuer, then re-loads the user and rejects the token if the user is gone or the role changed. Roles are enforced in `SecurityConfig`; product ownership is enforced in the services using the principal, never client-supplied ids. See [AUTH_MIGRATION.md](AUTH_MIGRATION.md).

## Screenshots
No UI screenshots are committed (the files in `images/` are sample product photos). To add them: run the app, capture login, retailer dashboard, low-stock page and customer product list, save under `docs/screenshots/` and link them here.

## Known limitations
- **Data is lost on every restart** (H2 in memory) – re-create demo accounts before presenting.
- Google/Facebook login is intentionally disabled (needs server-side provider-token verification).
- No rate limiting on login/OTP sending; JWT stored in `localStorage`; no refresh tokens or server-side logout.
- The Gmail app password that was committed earlier is still in git history and must be revoked.
- Low-stock filtering is client-side (`quantity < 10`); no cart, orders or payments.
- Legacy products without an owner are read-only until an owner is assigned.
- Images are stored in the database and sent base64-encoded in list responses.
- Wholesale catalogue is publicly readable.
- `npm audit` reports vulnerabilities in dev/prod dependencies (axios is unused; react-router upgrade advisable).
