# Frontend Deployment — Dashboard on Netlify

The dashboard is a static SPA (`frontend/`, built with Vite). Netlify serves the
built files and **reverse-proxies** `/api/*` and `/actuator/*` to the API host, so
the browser sees one origin:

```
browser ──https──► Netlify ──┬─ static SPA (frontend/dist)
                             └─ /api/*, /actuator/*  ──http──► API host (EC2)
```

Same-origin means no CORS and the refresh cookie stays first-party. The proxy
rules live in [`netlify.toml`](../netlify.toml) at the repo root.

## Prerequisites

1. A Netlify account with access to this GitHub repo.
2. **A stable API address.** The EC2 public IP changes on stop/start; if the proxy
   target in `netlify.toml` goes stale, the whole dashboard breaks. Allocate an
   **Elastic IP** (or a DNS name) and use it as the `to =` host in `netlify.toml`.

## 1. Connect the site

Netlify → **Add new site → Import an existing project** → pick this repo. Netlify
reads `netlify.toml`, so the build settings are already correct:

- Base directory: `frontend`
- Build command: `npm run build`
- Publish directory: `frontend/dist`

Deploy. Every push to `main` then rebuilds and redeploys the dashboard
automatically (Netlify's own CI — separate from the backend Jenkins pipeline).

> Netlify builds with the public npm registry, so the CodeArtifact `~/.npmrc` on
> your laptop is irrelevant here. No `.npmrc` is committed.

## 2. Point the proxy at a stable API host

In `netlify.toml`, set both `to =` hosts to your stable address, e.g.:

```toml
to = "http://<elastic-ip-or-dns>/api/:splat"
to = "http://<elastic-ip-or-dns>/actuator/:splat"
```

Commit + push; Netlify redeploys.

## 3. Backend changes for the deployed origin

The API must accept the Netlify origin on its cookie endpoints. On the API host
env file (`/opt/taskforge/taskforge.env`) set:

```
# Netlify site origin — allows /auth/refresh and /auth/logout from the dashboard.
AUTH_CSRF_ORIGINS=https://<your-site>.netlify.app
# Browser talks to Netlify over HTTPS, so mark the refresh cookie Secure.
AUTH_COOKIE_SECURE=true
```

Then redeploy the API (Jenkins **Build with Parameters**, `UPDATE_ENV_FILE` ticked,
`DEPLOY_HOST` = current IP). CORS stays disabled — the proxy makes it same-origin.

## CLI alternative (manual deploy)

```bash
npm i -g netlify-cli
netlify login
cd frontend && npm install --registry https://registry.npmjs.org/
netlify deploy --build --prod
```

## Note on HTTPS end-to-end

The Netlify→API leg is plain HTTP today (the API has no TLS yet). It works because
the proxy is server-side, but the hop to EC2 is unencrypted. The HTTPS-hardening
milestone (CloudFront→ALB + a cert) closes that; until then, treat it as a
staging-grade setup.
