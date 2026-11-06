# CI — Minimal Pipeline (Jenkins)

This is the **day-one** CI for TaskForge (development order step 3): it builds the
backend and runs the integration tests (Testcontainers/PostgreSQL) on every push
and pull request, so authentication tests run from the moment they exist.

The **full** Jenkins CI/CD pipeline (frontend build, `npm audit`, Netlify deploy,
smoke tests, rollback) is a later milestone (step 14) and will extend this
`Jenkinsfile` rather than replace it.

## What it does

| Stage | Command | Purpose |
|-------|---------|---------|
| Build | `mvn -B -ntp clean compile` | Fail fast on compile errors |
| Test  | `mvn -B -ntp verify` | Run unit + Testcontainers integration tests |

Test results are published via JUnit; the Maven repo is cached in a named Docker
volume (`taskforge-m2`) between builds.

## Agent prerequisites

The pipeline runs inside a `maven:3.9.9-eclipse-temurin-21` container and needs a
**Docker daemon** for Testcontainers. The `Jenkinsfile` uses Docker-outside-of-Docker:

- `-v /var/run/docker.sock:/var/run/docker.sock` — Testcontainers starts the
  ephemeral Postgres container as a **sibling** on the host daemon.
- `--network host` — the JVM reaches the Postgres container's published port on
  `localhost`.

So the Jenkins node must have:
1. The **Docker Pipeline** plugin installed.
2. A Docker daemon running, with the Jenkins user able to access
   `/var/run/docker.sock`.

## Setup

1. Jenkins → **New Item** → **Multibranch Pipeline** (or Pipeline).
2. Point it at this repository; Jenkins auto-discovers the root `Jenkinsfile`.
3. Enable **"Build on push"** (webhook) so CI runs on every push/PR.

## Running the tests locally

```bash
# Requires a running Docker daemon
cd backend
mvn verify
```

## Notes / future hardening

- Add `npm ci && npm run lint && npm test && npm audit` + the Vite build once the
  frontend exists.
- Add Netlify deploy + smoke-test + rollback stages at step 14. Netlify auth token
  and site ID come from **Jenkins credentials only** (never the repo).
