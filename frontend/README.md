# TaskForge Dashboard

React + Vite + Tailwind + TanStack Query SPA over the TaskForge API. No TypeScript,
no Next.js (per the project's stack constraints).

## Run locally

```bash
cd frontend
npm install
cp .env.example .env   # point VITE_API_TARGET at your backend
npm run dev            # http://localhost:5173
```

The dev server proxies `/api/*` and `/actuator/*` to `VITE_API_TARGET`
(default `http://localhost:8080`). Keeping the API same-origin means the refresh
cookie stays first-party and the browser `Origin` matches the backend's CSRF
allowlist (`http://localhost:5173`).

To hit the live API instead, set `VITE_API_TARGET=http://<api-host-ip>` in `.env`.

## What it does

- **Auth** — register (starts as VIEWER), sign in, sign out. The access token is
  held in memory only; the refresh token is an HttpOnly cookie. A 401 triggers one
  transparent refresh-and-retry; on reload the session is restored from the cookie.
- **Jobs** — submit a job (pick a queue, JSON payload), and watch the list
  auto-refresh as the worker drives it PENDING → RUNNING → SUCCEEDED / DEAD. Retry
  failed/dead jobs (needs RETRY permission or ADMIN).
- **Queues** — list queues you can see; admins create queues and grant per-user
  queue permissions (VIEW/SUBMIT/MANAGE/RETRY).
- **Admin** — admins grant/revoke user roles via the role endpoint (the Admin tab
  only shows for ADMINs).

## Build

```bash
npm run build     # outputs to dist/
```

## Deploy (later milestone)

Netlify serves `dist/` and reverse-proxies `/api/*` to the API host, so the SPA and
API are same-origin in production (no CORS, first-party cookie). That proxy config
is part of the deployment milestone, not included here yet.
