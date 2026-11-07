# Deployment — Backend CI/CD (Jenkins on EC2 → Docker Hub)

This sets up **continuous delivery on every git push**: a GitHub webhook triggers
Jenkins (running on an EC2 instance), which builds the backend, runs the tests,
builds a Docker image and pushes it to **Docker Hub**.

```
GitHub push ──webhook──► Jenkins (EC2) ──► mvn build+test ──► docker build ──► docker push → Docker Hub
```

> Scope: this pushes the **image**. Running that image on a server (ECS/VM) is a
> separate step, deferred for now (ARCHITECTURE.md step 16). The frontend
> (Netlify) is a later milestone — no frontend exists yet.

## Prerequisites you must provide

1. **AWS credentials** that work (`aws sts get-caller-identity` must succeed).
2. **An EC2 key pair** in your target region (for SSH / reading the Jenkins password).
3. **A Docker Hub account** + an **access token** (Docker Hub → Account Settings →
   Security → New Access Token). Used by Jenkins to push.
4. **A GitHub repository** (we push this repo to it) and permission to add a webhook.

## 1. Provision Jenkins on EC2

```bash
KEY_NAME=<your-ec2-keypair> AWS_REGION=us-east-2 SSH_CIDR=<your-ip>/32 \
  ./infra/deploy-jenkins.sh
```

This creates (via `infra/jenkins-ec2.yaml`): a security group (22 + 8080), an
IAM role (SSM Session Manager), and a `t3.medium` Amazon Linux 2023 instance whose
user-data installs Docker, JDK 21, Maven, Git and Jenkins, and adds the `jenkins`
user to the `docker` group.

The stack outputs the **Jenkins URL** and the command to read the initial admin
password. First boot takes a few minutes while packages install.

> Cost note: a running `t3.medium` + 30 GB gp3 is billable. `aws cloudformation
> delete-stack --stack-name taskforge-jenkins` tears it all down.

## 2. Configure Jenkins

1. Open the Jenkins URL, unlock with the initial admin password, install
   suggested plugins, and create an admin user.
2. Install plugins (if not already): **Git**, **Pipeline**, **Docker Pipeline**,
   **GitHub** (and **GitHub Branch Source** for multibranch).
3. Add credentials → **Username with password**, id **`dockerhub-creds`**:
   username = your Docker Hub username, password = the **access token**.
4. Edit `Jenkinsfile` → set `DOCKERHUB_REPO` to `your-dockerhub-username/taskforge-backend`.

## 3. Create the Jenkins job

Use a **Multibranch Pipeline** (recommended) pointed at the GitHub repo. Jenkins
auto-discovers the root `Jenkinsfile` and builds every branch/PR. Enable
**"Scan by webhook"** or periodic scanning.

## 4. Wire the GitHub webhook (build on every push)

- GitHub repo → Settings → Webhooks → Add webhook
- Payload URL: `http://<jenkins-public-dns>:8080/github-webhook/`
- Content type: `application/json`
- Events: **Just the push event** (add PRs if desired)

Push a commit; Jenkins should start a build within seconds, ending with the image
pushed to Docker Hub (`DOCKERHUB_REPO:<git-sha>`, plus `:latest` on `main`).

## Auto-deploy (the Deploy stage)

On `main`, after the image is pushed, the Jenkins **Deploy** stage SSHes to the API
host and rolls the container to the new image:

```
docker pull <repo>:<sha>  →  docker rm -f taskforge-api  →  docker run ... --env-file /opt/taskforge/taskforge.env
```

One-time setup to enable it:

1. **Host env file** — create `/opt/taskforge/taskforge.env` on the API host from
   `deploy/taskforge.env.example` (root-owned, `chmod 600`). Holds the Neon creds etc.
   Secrets live here only, never in the repo. (Or let the pipeline push it — see
   "New / changed host IP" below.)
2. **Host Docker login** — the host must be logged in to Docker Hub to pull the
   private image: `sudo docker login -u <user>` (re-run if the token is rotated).
3. **Jenkins SSH credential** — add id **`taskforge-host-ssh`**, kind *SSH Username
   with private key*, username `ubuntu`, private key = the host's `.pem`.
4. **Jenkins secret file** (optional, for pushing the env file) — add id
   **`taskforge-host-env`**, kind *Secret file*, uploading your real
   `taskforge.env`. Used only when the **`UPDATE_ENV_FILE`** build parameter is set.
5. Host security group must allow SSH (22) from the Jenkins box.

After a push to `main`: build → test → image pushed → **host pulls + restarts** the
container automatically (using the default `DEPLOY_HOST`).

### New / changed host IP

The host's public IP changes on stop/start. The Deploy stage takes two build
parameters (visible under **Build with Parameters** from the second build onward):

- **`DEPLOY_HOST`** — the IP/DNS to deploy to. Override it to point at the new host.
- **`UPDATE_ENV_FILE`** — tick this on a fresh host so the pipeline copies the env
  file to `/opt/taskforge/taskforge.env` over SSH (from the `taskforge-host-env`
  secret file) before rolling the container.

So when the IP changes: **Build with Parameters** → set `DEPLOY_HOST` to the new IP,
tick `UPDATE_ENV_FILE` (first deploy to that host), Build. A push-triggered build
always uses the defaults, so to avoid this each time, allocate an **Elastic IP** (or
a DNS name) and set it as the `DEPLOY_HOST` default. A brand-new host also needs the
SSH key authorized, Docker installed + logged in, and SG port 22 open to Jenkins.

## Security notes

- Narrow `SshAllowedCidr` to your IP. Keep 8080 reachable for GitHub webhooks, or
  use GitHub's published webhook IP ranges.
- The Docker Hub token lives only in Jenkins credentials, never in the repo.
- Prefer a Docker Hub **access token** over your account password.
