# TaskForge — Architecture & Requirements (v2)

**Status:** Draft for approval · **Date:** 2026-11-06
**Scope of this document:** Hosting, authentication, authorization, database schema, security, and the Netlify + Jenkins deployment architecture. **No code is written until this is approved.**

> For every subsystem below the reasoning follows the required progression:
> **Brute-force → Limitations → Improved → Optimal practical design → Implementation → Testing → Engineering review.**

---

## 0. Global Constraints (recap)

| Area | Decision |
|---|---|
| Frontend | React, JavaScript ES6+, Vite, Tailwind, React Router, TanStack Query. **No Next.js, no TypeScript.** |
| Backend | Spring Boot, Spring Security, PostgreSQL. |
| Auth providers | None external initially (self-hosted auth). |
| Deploy | Frontend and backend deploy **independently**. |
| Secrets | **Never** in frontend env. `VITE_*` is baked into the public bundle. All secrets live in Jenkins credentials / AWS Secrets Manager. |

---

## 1. System Architecture Overview

### 1.1 Components (Section H)

```
                          ┌──────────────────────────────────────────────┐
                          │                 Browser (SPA)                 │
                          │  React + Vite bundle, access token in memory  │
                          └───────────────────────┬──────────────────────┘
                                                  │ HTTPS, same-origin
                                                  │ relative URLs: /api/v1/...
                          ┌───────────────────────▼──────────────────────┐
                          │              Netlify CDN + Edge               │
                          │  • Static SPA (dist/)                          │
                          │  • SPA fallback  /*  -> /index.html (200)      │
                          │  • Reverse proxy /api/*  -> backend (200,force)│
                          │  • Injects X-Proxy-Secret header              │
                          └───────────────────────┬──────────────────────┘
                                                  │ HTTPS (public, no custom domain)
                                                  │ X-Proxy-Secret required
             ┌────────────────────────────────────▼─────────────────────────────────┐
             │                        AWS (backend edge)                              │
             │   CloudFront (*.cloudfront.net HTTPS)  ──►  ALB  ──►  Spring Boot API   │
             │   (verifies X-Proxy-Secret; forwards X-Nf-Client-Connection-Ip)        │
             └────────────────────────────────────┬─────────────────────────────────┘
                                                  │
        ┌─────────────────────────────────────────┼───────────────────────────────────────┐
        │                   Spring Boot API (Spring Security)                                │
        │  Auth · RBAC · Job submission · Queue mgmt · Worker mgmt · Retry/DLQ · Scheduler   │
        └───┬───────────────┬───────────────┬────────────────┬──────────────────┬──────────┘
            │               │               │                │                  │
     ┌──────▼──────┐  ┌─────▼─────┐  ┌───────▼──────┐  ┌──────▼──────┐  ┌────────▼────────┐
     │ PostgreSQL  │  │  Queue    │  │   Worker     │  │  Scheduler  │  │  Rate limiter   │
     │ (persistence│  │  engine   │  │   runtime    │  │             │  │ Bucket4j (Redis │
     │  + Flyway)  │  │ (custom)  │  │  (custom)    │  │             │  │  or Postgres)   │
     └─────────────┘  └─────┬─────┘  └──────────────┘  └─────────────┘  └─────────────────┘
                            │
                      ┌─────▼─────┐
                      │ Retry eng.│──► Dead-letter queue (DLQ)
                      └───────────┘
```

**Component list (H):**
1. React frontend on Netlify
2. Spring Boot API
3. Spring Security authentication
4. PostgreSQL persistence
5. Custom queue engine
6. Custom worker runtime
7. Scheduler
8. Retry engine
9. Dead-letter queue
10. Jenkins CI/CD
11. AWS infrastructure
12. Multi-region deployment *(future milestone)*

### 1.2 Development order (Section I)

Authentication ships **before any job API is exposed**; minimal CI exists from day one so auth tests run immediately.

`Architecture → DB foundation → Minimal CI → Auth/Authz → Job APIs → Queue engine → Worker runtime → Visibility timeout & ack → Retry & DLQ → Scheduler → React dashboard → FE auth integration → Dockerize → Full Jenkins CI/CD → Netlify deploy → AWS deploy → Monitoring → Multi-region.`

---

## 2. Domain & Cookie Topology, CORS Design (Section B)

### 2.1 Problem

Netlify (`*.netlify.app`) and the AWS API (`*.cloudfront.net`) are **different sites**. No custom domain will be purchased. A naive design would make the browser call the AWS URL directly, forcing `SameSite=None` third-party cookies (increasingly blocked) and a CORS allowlist.

**Brute-force:** Browser → AWS URL directly, `SameSite=None; Secure` cookies, CORS allowlist of the Netlify origin.
**Limitations:** Third-party cookies are blocked by Safari ITP / Firefox and soon broadly; preview URLs change per PR so the CORS allowlist and cookie domain churn constantly; the API URL is baked into the bundle (`VITE_API_URL`), leaking topology and making env-per-deploy mandatory.

**Chosen (optimal practical) design — Netlify reverse-proxy rewrite.**
The browser only ever talks to the Netlify origin. Netlify proxies `/api/*` to the backend with a status-200 rewrite. Every request is therefore **same-origin**, so cookies are **first-party** and **no production CORS is needed**.

### 2.2 `netlify.toml` redirect order (API proxy MUST precede SPA fallback)

```toml
[build]
  command = "npm run build"
  publish = "dist"

# 1) API proxy — same-origin rewrite. Must come BEFORE the SPA fallback.
[[redirects]]
  from = "/api/*"
  to = "https://<backend-https-url>/api/:splat"
  status = 200
  force = true
  headers = { X-Proxy-Secret = "<from Netlify env, not repo>" }

# 2) SPA fallback — nested routes resolve to index.html.
[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200

# Per-context backend targets
[context.production.environment]
  # production build points proxy at production API
[context.deploy-preview]
  # PR previews proxy to STAGING API, never production
[context.branch-deploy]
  # staging branch proxies to STAGING API
```

> Context overrides (`[context.production]` / `[context.deploy-preview]` / `[context.branch-deploy]`) set the proxy target so **production proxies to the production API** and **previews + staging proxy to the staging API**. Previews never touch production.

### 2.3 Cookie design (consequence of same-origin)

| Attribute | Value | Reason |
|---|---|---|
| `Secure` | yes | HTTPS only. |
| `HttpOnly` | yes | JS cannot read the refresh token (XSS can't exfiltrate it). |
| `SameSite` | `Strict` | Refresh/logout are top-level same-site; blocks CSRF cross-site sends. |
| `Domain` | *(omitted — host-only)* | Scope to the exact origin. |
| `Path` | `/api/v1/auth` | Cookie only sent to auth endpoints, not every API call. |

The frontend uses **relative URLs** (`/api/v1/...`), so **no API URL is baked into the bundle**.

### 2.4 CORS

- **Production:** not required (same-origin). No CORS config shipped to prod.
- **Local dev:** strict explicit allowlist, e.g. `http://localhost:5173`. **No wildcards.** `allowCredentials=true` only with that explicit origin.

### 2.5 Backend public HTTPS without a custom domain

An ALB cannot terminate HTTPS without a domain certificate. Two options:

| Option | HTTPS hostname | Pros | Cons |
|---|---|---|---|
| **CloudFront → ALB** *(chosen)* | free `*.cloudfront.net` | Keeps standard ALB/ECS; can verify `X-Proxy-Secret` at the edge; caching/WAF attach later; forwards headers cleanly. | Extra hop; cache behaviors must be set to forward auth headers & not cache API. |
| API Gateway HTTP API | `*.execute-api.amazonaws.com` | Native HTTPS; cheap; built-in throttling. | Different integration model; 29 s timeout; less natural in front of a long-running ALB/ECS service. |

**Decision (confirmed):** CloudFront in front of the ALB. API cache behavior: **no caching**, forward all headers/cookies, forward the proxy secret.

### 2.6 Locking the backend origin

- Netlify injects `X-Proxy-Secret` via the redirect rule. **CloudFront (or the API) rejects any request missing/mismatching it** → the backend cannot be hit directly, bypassing the proxy.
- Secret is stored in Netlify env + AWS (Secrets Manager / CloudFront function env), **never in the repo**. Rotatable.

### 2.7 Client IP for rate limiting & audit

The backend sees **Netlify/CloudFront IPs**, not the user's. Read the real client IP from the forwarded header (`X-Nf-Client-Connection-Ip`), and **trust it only when `X-Proxy-Secret` is valid**. Otherwise per-IP limits and audit IPs are wrong/spoofable.

### 2.8 Known limits to design around

- Netlify proxy requests **time out at ~26 s** → no long requests through the proxy.
- **No WebSocket / SSE** through the proxy → the dashboard uses **polling / short requests**, not long-lived connections.
- One extra network hop (acceptable).

### 2.9 Future

If a domain is purchased later, the **cookie + relative-URL design stays valid**. Replace the proxy with sibling subdomains (`app.` / `api.`) and a shared parent-domain cookie **without frontend changes**.

---

## 3. Authentication Flows (Section C)

### 3.1 Token model

**Access token (JWT)**
- Short-lived **15 min** *(decided)*, held **in frontend memory only**, sent as `Authorization: Bearer`.
- **Pinned signing algorithm** (e.g. `RS256` or `EdDSA`); validate signature, `exp`, `iss`, `aud`; include `kid` for key rotation.
- Signing key in **AWS Secrets Manager** (local dev: env/config).
- **Stale-roles trade-off:** role claims can be stale until expiry. **Mitigation:** short TTL **+** server-side re-check of role/permission on sensitive operations (role changes, admin actions) rather than trusting the JWT claim alone.

**Refresh token (opaque)**
- Opaque random **256-bit** value (not a JWT), in a **Secure HttpOnly** cookie.
- Stored in Postgres as a **SHA-256 hash** (token is already high-entropy, so no Argon2/BCrypt needed).
- **Rotated on every refresh**; old token marked `used`.
- **Sliding expiry 14 days** + **absolute session cap 30 days** *(decided)*.
- **Revoked on logout.**
- **Replay detection:** reuse of an already-rotated token → **revoke the whole token family** and force re-login; audit-logged.
- **Multi-tab race:** **30 s** server-side **grace window** *(decided)* on the just-rotated token (reuse within the window by the same family is not treated as attack) + client-side **single-flight** refresh (Web Locks API / BroadcastChannel).

### 3.2 CSRF for cookie endpoints (`/refresh`, `/logout`)

`SameSite=Strict` alone is not sufficient. **Also require:**
- a **custom request header** (e.g. `X-CSRF: 1`, which forces a preflight and can't be sent by a simple cross-site form) **or** a double-submit token, **and**
- **`Origin` header validation** against the allowed origin.

### 3.3 Endpoints

| Method & path | Auth | Request | Success | Errors | Cookies |
|---|---|---|---|---|---|
| `POST /api/v1/auth/register` | public | `{email, password, name}` | `201 {id, email, name, roles:["VIEWER"]}` | `409` email taken, `422` weak password / invalid | none |
| `POST /api/v1/auth/login` | public | `{email, password}` | `200 {accessToken, expiresIn, user{...}}` | `401` bad creds (generic), `429` throttled | **Set-Cookie** refresh (Secure,HttpOnly,SameSite=Strict,Path=/api/v1/auth) |
| `POST /api/v1/auth/refresh` | cookie + CSRF | *(empty body)* | `200 {accessToken, expiresIn}` | `401` missing/expired/invalid; replay → `401` + family revoked | **Set-Cookie** rotated refresh |
| `POST /api/v1/auth/logout` | cookie + CSRF | *(empty)* | `204` | `401` | **Set-Cookie** cleared (Max-Age=0) |
| `GET /api/v1/auth/me` | Bearer | — | `200 {id, email, name, roles, status}` | `401` | none |

**Error format (uniform):**
```json
{ "error": { "code": "INVALID_CREDENTIALS", "message": "Email or password is incorrect", "requestId": "..." } }
```
Login failures are **generic** (never reveal whether the email exists).

### 3.4 Flow diagrams

**Register**
```
Client ──POST /register {email,password,name}──► API
                         API: validate → Argon2id(password) → insert user (status=ACTIVE)
                              → attach role VIEWER → audit(REGISTER)
       ◄──────────── 201 {id,email,name,roles:[VIEWER]} ──────────
(No auto-login (decided): client then calls /login. Keeps register/login single-purpose.)
```

**Login**
```
Client ──POST /login {email,password}──► API
             rate-limit check (per-IP + per-account, progressive delay)
             find user by lower(email); Argon2id.verify(password, hash)
             if ok: issue access JWT (10–15m)
                    create refresh token (random 256-bit), store SHA-256 hash,
                        family_id=new, expires_at=+7–14d, absolute=+30d
                    audit(LOGIN_SUCCESS)   | else audit(LOGIN_FAIL, user null if unknown)
   ◄── 200 {accessToken, expiresIn, user} + Set-Cookie: refresh=<opaque> ──
```

**Authenticated request**
```
Client ──GET /api/v1/jobs  (Authorization: Bearer <access>)──► API
             Spring Security filter: validate sig, exp, iss, aud, alg(kid)
             populate SecurityContext (userId, roles)
             @PreAuthorize role gate → service-layer ownership/permission check
   ◄── 200 data   |  401 (bad/expired token)  |  404 (not permitted, see §5) ──
```

**Refresh with rotation**
```
Client ──POST /refresh  (Cookie: refresh=<opaque>; X-CSRF:1; Origin check)──► API
             hash = SHA-256(cookie); look up refresh_tokens by token_hash
             if not found/expired/revoked → 401
             if used_at set (already rotated):
                   if within grace window & same family → return current valid token (multi-tab)
                   else  → REPLAY: revoke entire family_id, audit(REPLAY_DETECTED) → 401
             else (fresh):
                   mark used_at=now, replaced_by=<new>
                   create new refresh (same family_id), new access JWT
   ◄── 200 {accessToken, expiresIn} + Set-Cookie: refresh=<new rotated> ──
```

**Replay detection**
```
Attacker replays an already-rotated token
   → token row has used_at != null
   → NOT within grace window (or different family signal)
   → revoke ALL tokens WHERE family_id = <that family>  (set revoked_at)
   → audit(REPLAY_DETECTED, user, ip, request_id)
   → 401; user must log in again (family-wide)
```

**Logout**
```
Client ──POST /logout (Cookie + CSRF)──► API
             revoke current refresh token (revoked_at=now); optionally whole family
             audit(LOGOUT)
   ◄── 204 + Set-Cookie: refresh cleared (Max-Age=0) ──
Client clears in-memory access token + TanStack Query cache.
```

**App-boot silent refresh**
```
App loads → access token is gone (memory lost on reload)
   → AuthProvider sets state = "bootstrapping"
   → POST /refresh (cookie sent automatically)
         200 → store new access token in memory → state="authenticated" → GET /me
         401 → state="anonymous"
   → Protected routes WAIT for bootstrap to finish before deciding redirect-to-login.
```

### 3.5 Registration & role assignment

- **Self-registration always creates `VIEWER`.**
- **`ADMIN` only via a seed/bootstrap command** (CLI / Flyway seed / `ApplicationRunner` gated by an env flag), **never** the public API.
- **Role changes only via an admin-only endpoint**, and **audit-logged**.

### 3.6 Explicitly out of scope (future milestones — do not creep in)

Password reset · email verification · "logout all devices" · MFA · OAuth / social login.

---

## 4. RBAC — Roles & Enforcement (Section D)

### 4.1 Roles

- **ADMIN** — full platform admin: user mgmt, queue mgmt, worker mgmt, job submission, retry/redrive, infra monitoring.
- **DEVELOPER** — submit jobs, view permitted jobs, inspect execution history, manage permitted queues, retry permitted jobs.
- **VIEWER** — read-only dashboard, view permitted jobs/queues, view execution metrics.

### 4.2 Enforcement rules

- Authorization enforced in the **backend** at **API and resource level**. Frontend route guards / hidden buttons are **UX only, never security**.
- **Layering:** `@PreAuthorize` / method security for **role** checks **+** **ownership & permission** checks in the **service layer** for **every** resource access.
- **Never trust client-supplied resource IDs** without an ownership/permission check.
- Resources a user may not see → return **404, not 403** (prevents ID enumeration).
- **UUIDs** for all public resource IDs.

### 4.3 RBAC matrix (role × endpoint/action)

Legend: ✅ allowed · 🔒 allowed **only** on resources the user owns or has the matching queue permission · ❌ forbidden (→ 403 for role gate, or 404 for resource-level hiding).

| Action / Endpoint | ADMIN | DEVELOPER | VIEWER |
|---|:--:|:--:|:--:|
| `POST /auth/register` (public) | ✅ | ✅ | ✅ |
| `POST /auth/login` /`/refresh`/`/logout` | ✅ | ✅ | ✅ |
| `GET /auth/me` | ✅ | ✅ | ✅ |
| `GET /users` (list) | ✅ | ❌ | ❌ |
| `PATCH /users/{id}/roles` (role change) | ✅ | ❌ | ❌ |
| `PATCH /users/{id}/status` (disable) | ✅ | ❌ | ❌ |
| `POST /jobs` (submit) | ✅ | 🔒 (to permitted queue) | ❌ |
| `GET /jobs` / `GET /jobs/{id}` | ✅ | 🔒 | 🔒 |
| `POST /jobs/{id}/retry` | ✅ | 🔒 (RETRY perm) | ❌ |
| `POST /jobs/{id}/redrive` (from DLQ) | ✅ | ❌ | ❌ |
| `GET /queues` / `GET /queues/{id}` | ✅ | 🔒 (VIEW) | 🔒 (VIEW) |
| `POST /queues` (create) | ✅ | ❌ | ❌ |
| `PATCH /queues/{id}` (manage) | ✅ | 🔒 (MANAGE) | ❌ |
| `GET /queues/{id}/metrics` | ✅ | 🔒 (VIEW) | 🔒 (VIEW) |
| `GET /workers` / worker mgmt | ✅ | ❌ | ❌ |
| Infra / health monitoring (admin) | ✅ | ❌ | ❌ |

> 🔒 resolves to **404** when the user has no ownership/permission on the specific resource, and to **403** when the role is categorically not allowed the action at all.

---

## 5. Database Schema & Ownership Model (Section E)

Migrations via **Flyway** (versioned `V__` scripts). UUID PKs (`uuid` type, `gen_random_uuid()` / app-generated). All timestamps `timestamptz`.

### 5.1 Tables

**`users`**
| Column | Type | Constraints |
|---|---|---|
| `id` | uuid | PK |
| `email` | `citext` | **unique** (case-insensitive) — or `lower(email)` unique index |
| `password_hash` | text | not null |
| `name` | text | not null |
| `status` | text/enum | `ACTIVE`, `DISABLED` (check constraint or PG enum) |
| `created_at` | timestamptz | default now() |
| `updated_at` | timestamptz | default now() |

**`roles`**
| `id` | int/uuid | PK |
| `name` | text | **unique** (`ADMIN`/`DEVELOPER`/`VIEWER`) |
| `description` | text | |

**`user_roles`**
| `user_id` | uuid | FK→users, **composite PK** |
| `role_id` | int/uuid | FK→roles, **composite PK** |

**`refresh_tokens`**
| Column | Type | Notes |
|---|---|---|
| `id` | uuid | PK |
| `user_id` | uuid | FK→users, **index** |
| `token_hash` | text | **unique** (SHA-256 of opaque token) |
| `family_id` | uuid | **index** (rotation lineage) |
| `expires_at` | timestamptz | sliding expiry, **index** |
| `absolute_expires_at` | timestamptz | hard session cap |
| `used_at` | timestamptz | null until rotated |
| `replaced_by` | uuid | **self-FK**, nullable |
| `revoked_at` | timestamptz | nullable |
| `created_at` | timestamptz | |
| `user_agent` | text | |
| `ip` | inet/text | real client IP (from trusted forwarded header) |

Indexes: `user_id`, `family_id`, `expires_at`.

**`audit_logs`** (append-only; the app DB role has **no UPDATE/DELETE** grant)
| `id` | uuid | PK |
| `user_id` | uuid | **nullable** (failed logins have no user) |
| `action` | text | e.g. `LOGIN_FAIL`, `REPLAY_DETECTED`, `ROLE_CHANGE` |
| `resource_type` | text | |
| `resource_id` | text/uuid | |
| `ip` | inet/text | |
| `request_id` | text | correlation id |
| `metadata` | jsonb | structured extra context |
| `created_at` | timestamptz | |

Index: `(user_id, created_at)`.

### 5.2 Ownership & permissions

**`jobs`** and **`queues`** each carry `owner_id` (FK→users) and a **nullable `workspace_id`** (added now so the future organizations/workspaces multi-tenancy milestone is not a painful migration).

**`queue_permissions`**
| `queue_id` | uuid | FK→queues, part of unique key |
| `user_id` | uuid | FK→users, part of unique key |
| `permission` | text/enum | `VIEW`, `SUBMIT`, `MANAGE`, `RETRY` |

Unique on `(queue_id, user_id, permission)`; index on `(user_id)`.

### 5.3 Job visibility rule (defined explicitly)

> A user may **see** a job (`GET /jobs/{id}` → 200) **iff** any of:
> 1. the user is **ADMIN**, **or**
> 2. `job.owner_id == user.id`, **or**
> 3. the user holds **`VIEW`** (or higher) permission on `job.queue_id` via `queue_permissions`.
>
> Otherwise → **404**. The same predicate filters `GET /jobs` list results.
> **Submit** requires `SUBMIT` on the target queue (or ADMIN). **Retry** requires `RETRY` on the job's queue, or ADMIN (ownership alone does not grant retry). **Redrive** from DLQ is ADMIN-only.

---

## 6. Security Decisions & Trade-offs (Section G)

| Item | Decision | Trade-off / notes |
|---|---|---|
| **Password hashing** | **Argon2id** (memory-hard). Params target ~**64 MB, t=3, p=1**, tune to ~250 ms on prod hardware. BCrypt (cost ≥12) as fallback if Argon2 lib unavailable. | Argon2 resists GPU cracking better; higher memory per hash — size the auth instances accordingly. |
| **Password policy** | Min length (≥12), reject breached/common passwords (local list), no composition nags. | Length > complexity rules. |
| **Brute-force / rate limiting** | **Bucket4j**, **Postgres-backed for v1** *(decided)* (migrate to Redis when login volume grows), **per-account + per-IP**, **progressive delay** (not hard lockout). AWS WAF in front later. | Progressive delay prevents attackers **locking out real users**; hard lockout is a DoS vector. Per-IP uses the trusted forwarded IP (§2.7). Postgres store avoids provisioning ElastiCache before it's needed. |
| **JWT validation** | Pinned alg, verify sig/`exp`/`iss`/`aud`, `kid`-based key rotation. | Pinning alg blocks `alg:none` / alg-confusion attacks. |
| **Refresh replay** | Opaque, rotated, family revocation on reuse + grace window for multi-tab. | Grace window is a deliberate small weakening to avoid false alarms. |
| **CSRF** | SameSite=Strict **+** custom header/double-submit **+** Origin check on cookie endpoints. | Defense in depth; SameSite alone insufficient. |
| **CORS** | None in prod (same-origin). Strict localhost allowlist in dev, no wildcards, credentials only with explicit origin. | |
| **SQL injection** | **Parameterized queries / JPA only.** No string-concatenated SQL. | |
| **Authz bypass / IDOR** | Service-layer ownership+permission on every access; **404 not 403**; UUID IDs. | |
| **Sensitive-data logging** | Never log passwords, tokens, or hashes. Redaction filter on logs; structured logs carry `request_id`, not secrets. | |
| **Secure cookies** | Secure, HttpOnly, SameSite=Strict, host-only, `Path=/api/v1/auth`. | |
| **Security headers** | Strict CSP (access token in memory → XSS is the main threat), HSTS, `X-Content-Type-Options`, `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy` — on **both** Netlify and the API. | |

### 6.1 Testing requirements (Testcontainers + PostgreSQL)

Integration tests (real Postgres via Testcontainers) covering **at minimum**:
- Registration, login, refresh rotation, logout, expiry.
- Refresh **replay detection** (family revocation) **and** the multi-tab **grace window**.
- Protected endpoints reject **missing / invalid / expired** tokens.
- **Role matrix**: each role can and cannot call each endpoint exactly as specified in §4.3.
- **IDOR**: a user cannot access another user's job/queue by changing the ID → **404**.
- **CORS** and **CSRF** rejection cases.
- **Rate limiting / brute-force** behavior (progressive delay, per-account + per-IP).

---

## 7. Netlify + Jenkins Deployment Architecture (Section A)

### 7.1 Pipeline

```
GitHub ──► Jenkins ──► install deps ──► lint ──► frontend tests ──► npm audit
       ──► Vite production build ──► netlify deploy (dist/) ──► Netlify CDN ──► smoke test
```

- Jenkins deploys via **Netlify CLI**: `netlify deploy --dir=dist` (`--prod` for production, `--alias <name>` for previews).
- **Netlify auth token + site ID live in Jenkins credentials only.**
- **Netlify's own Git auto-build is disabled** → deployments triggered **only** by Jenkins (no double deploys).

### 7.2 Environments

| Environment | Trigger | Proxy target (API) |
|---|---|---|
| `production` | Jenkins on main (`--prod`) | **production API** |
| `staging` | Jenkins on staging branch | **staging API** |
| PR **preview** | Jenkins per-PR (`--alias`) | **staging API** (never production) |

Each environment is **built separately** so it gets its own API proxy target via `netlify.toml` context overrides. Because the proxy makes every deploy same-origin, **cookies/auth work on preview URLs with no CORS allowlist changes**.

### 7.3 SPA fallback

`/* → /index.html (200)` so refreshing a nested route (e.g. `/jobs/123`) does **not** 404. (API proxy rule ordered **before** this — see §2.2.)

### 7.4 Security headers (`netlify.toml` / `_headers`)

Strict **CSP** (access token in memory → XSS is primary threat), **HSTS**, `X-Content-Type-Options: nosniff`, `X-Frame-Options`/`frame-ancestors 'none'`, `Referrer-Policy: strict-origin-when-cross-origin`.

### 7.5 Deployment verification & rollback

**Post-deploy smoke test:**
- root `/` returns **200**,
- a nested route (`/jobs/123`) returns **200** on **direct load**,
- app config points at the **correct API URL** (relative `/api` resolves to the right backend per context),
- API **health endpoint reachable** through the proxy.

**Rollback (documented):** restore the **previous Netlify deploy** (Netlify deploy history / `netlify rollback` or redeploy the prior build). Backend rolls back independently (separate pipeline).

---

## 8. Frontend Authentication Integration (Section F)

- **Pages:** login, registration, plus `401` Unauthorized and `403` Forbidden pages.
- **AuthContext** holds `{status, user, accessToken(in memory)}`; `status ∈ bootstrapping | authenticated | anonymous`.
- **Boot flow:** access token is lost on reload → app **silently calls `/refresh` on startup**; protected routes **wait on `bootstrapping`** before redirecting to login.
- **Auto-refresh on 401:** HTTP-client interceptor with **single-flight** — one in-flight refresh shared by all concurrent requests **and across tabs** (Web Locks / BroadcastChannel). On refresh failure → go anonymous, redirect to login with a clear "session expired" message.
- **Protected routes**, **role-based navigation**, **logout**.
- **Access token in memory only** — **no tokens or sensitive data in `localStorage`/`sessionStorage`**.
- **TanStack Query integration:** clear the query cache on logout; central auth-error handling (401 → refresh-or-logout, 403 → forbidden page).
- Route guards/hidden buttons are **UX only** (real enforcement is server-side, §4.2).

---

## 9. Risks, Open Questions, Deferred Items (Section J.8)

### 9.1 Risks
- **Netlify ~26 s proxy timeout + no WS/SSE** → dashboard must poll; long operations need async job + poll pattern, not synchronous long requests.
- **Proxy-secret leakage** would re-expose the backend → keep rotatable, out of repo; consider also restricting backend SG/CloudFront to limit exposure.
- **Argon2 memory cost** on auth instances → right-size compute; risk of resource exhaustion under login spikes (mitigated by rate limiting).
- **Stale JWT roles** until expiry → mitigated by short TTL + server-side re-check on sensitive ops.
- **CloudFront header/caching misconfig** could cache authenticated responses → explicit no-cache + forward-all-headers behavior for `/api/*`.

### 9.2 Resolved decisions (approved 2026-11-06)
1. **Backend edge:** **CloudFront → ALB** (free `*.cloudfront.net` HTTPS; verifies `X-Proxy-Secret`; no-cache + forward-all-headers on `/api/*`).
2. **Register response:** **No auto-login.** `/register` → `201` with the user; client then calls `/login`.
3. **Rate-limit store:** **Postgres-backed Bucket4j for v1**; migrate to Redis/ElastiCache when login volume grows.
4. **Multi-tab grace window:** **30 s**.
5. **Retry permission:** DEVELOPER retry requires explicit **`RETRY`** permission on the job's queue, **or** ADMIN. Ownership alone does not grant retry.
6. **Token lifetimes:** access **15 min**, refresh **sliding 14 days**, **absolute cap 30 days**. Stale JWT roles mitigated by short TTL + server-side re-check on sensitive ops.

### 9.3 Deferred (future milestones)
Password reset · email verification · logout-all-devices · MFA · OAuth/social login · organizations/workspaces multi-tenancy (schema hook `workspace_id` already added) · AWS WAF · multi-region deployment · custom domain (design already compatible).

---

*End of architecture document. Awaiting approval before any implementation.*
