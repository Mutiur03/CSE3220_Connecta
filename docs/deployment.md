# Deployment (VPS)

Connecta runs on one Linux VPS (Ubuntu 22.04 or 24.04) with Docker. GitHub Actions builds the images, pushes them to GHCR and
deploys over SSH on every push to `main`. The team chose a VPS instead of Render, so this is the "additional deployment
platform" of the course bonus and the auto-deploy requirement.

```
Internet -> Caddy (80/443, automatic HTTPS) -> frontend :3000   (APP_DOMAIN)
                                            -> app :8080        (API_DOMAIN, REST, /ws/inbox, Meta webhook)
                                            -> minio :9000      (FILES_DOMAIN, public bucket, GET/HEAD only)
app -> postgres :5432, minio :9000   (compose network "connecta"; only Caddy publishes ports)
```

Files: `deploy/docker-compose.prod.yml`, `deploy/Caddyfile`, `deploy/.env.production.example`, `scripts/vps-bootstrap.sh`,
`scripts/deploy-remote.sh`, `.github/workflows/deploy.yml`, plus `deploy/docker-compose.monitoring.yml`,
`scripts/backup-postgres.sh` and `scripts/restore-postgres.sh`. Running the server afterwards (logs, monitoring, backups, restore
drill, secrets, disk, runbook): [operations.md](operations.md).

## What is manual (nothing here is done by CI)
- Renting the VPS, the domain and DNS records.
- Running the bootstrap script once and writing the `.env` file on the server.
- Adding the GitHub secrets and variables below.
- Registering the Meta webhook and OAuth redirect URLs with the production hostnames.
- Optional monitoring (`MONITORING_ENABLED=true`) and an off-server copy of the backups, see [operations.md](operations.md).

Not verified yet: the whole chain was written without a real VPS. Expect to fix small things on the first real deploy.

## 1. DNS
Three records (A, plus AAAA if the VPS has IPv6) to the VPS address. Names are yours; these are examples:

| Name | Serves |
|---|---|
| `app.example.com` | web app (`APP_DOMAIN`) |
| `api.example.com` | backend API, WebSocket and Meta webhook (`API_DOMAIN`) |
| `files.example.com` | product images from MinIO (`FILES_DOMAIN`) |

DNS must resolve before the first start, otherwise Caddy cannot get certificates (it retries, so it recovers once DNS is right).

## 2. Prepare the server
1. Create the VPS, log in as root over SSH.
2. Make a key pair for GitHub Actions on your own machine (no passphrase): `ssh-keygen -t ed25519 -f connecta-deploy -C github-actions`.
3. Copy `scripts/vps-bootstrap.sh` to the server and run it:
   ```bash
   sudo DEPLOY_USER=deploy DEPLOY_PUBKEY="$(cat connecta-deploy.pub)" bash vps-bootstrap.sh
   ```
   It installs Docker and the compose plugin, creates the `deploy` user, opens only ports 22, 80 and 443 in ufw, enables unattended
   security upgrades and fail2ban, and creates `/opt/connecta`. It is safe to run again.
4. In a second terminal confirm `ssh -i connecta-deploy deploy@<host>` works before closing the root session.
5. Create the env file as the deploy user:
   ```bash
   scp deploy/.env.production.example deploy@<host>:/opt/connecta/.env
   ssh deploy@<host> "chmod 600 /opt/connecta/.env && nano /opt/connecta/.env"
   ```
   Replace every `CHANGE` value and the three domains. Generate secrets with `openssl rand -base64 48` (JWT_SECRET),
   `openssl rand -base64 32` (META_TOKEN_KEY) and `openssl rand -base64 24` (database and MinIO passwords).
   Every variable is documented inside the file.

## 3. GitHub configuration
Settings -> Secrets and variables -> Actions.

| Kind | Name | Value |
|---|---|---|
| Secret | `VPS_HOST` | server address or hostname |
| Secret | `VPS_USER` | `deploy` |
| Secret | `VPS_SSH_KEY` | contents of the private key `connecta-deploy` |
| Secret (optional) | `VPS_PORT` | SSH port if not 22 |
| Secret (optional) | `VPS_KNOWN_HOSTS` | output of `ssh-keyscan -p <port> <host>`; without it the workflow trusts the key it sees on each run |
| Variable | `NEXT_PUBLIC_API_URL` | `https://api.example.com` (baked into the frontend image at build time) |
| Variable (optional) | `VPS_DIR` | install directory, default `/opt/connecta` |

If a required secret or variable is missing, the workflow still goes green: the image build and deploy are skipped with a notice
that names what is missing.

Also set Settings -> Actions -> General -> Workflow permissions to "Read and write" (the workflow asks for `packages: write` itself).
The images are private by default in GHCR; the workflow logs the server in with the run's token for each deploy.

## 4. Deploy
Merge to `main` (the release PR). `deploy.yml` then:
1. runs the backend and frontend tests;
2. builds `connecta-backend` and `connecta-frontend`, tags them with the commit SHA and `latest`, and pushes to
   `ghcr.io/<owner>/` (Docker layer cache in the Actions cache);
3. copies `docker-compose.prod.yml`, `Caddyfile` and `deploy-remote.sh` to `/opt/connecta`;
4. runs `deploy-remote.sh <sha>` on the server: pull, `up -d`, wait up to 240 s for the backend container to be healthy
   (Flyway migrations run on start), then remember the tag in `.last-good-tag`.

First deploy: the database starts empty. Open `https://<APP_DOMAIN>/setup` to create the owner account.

## 5. Roll back
Automatic: if the backend is not healthy in time, `deploy-remote.sh` starts the previous tag again and the job fails.
The first deploy has no previous tag. Flyway migrations are not reverted: write migrations so the previous image still works
with the new schema (additive changes first).

By hand, to any tag that exists in GHCR:
```bash
ssh deploy@<host>
cd /opt/connecta && ./deploy-remote.sh <older-sha>
```

## 6. Logs and status
```bash
cd /opt/connecta
docker compose --env-file .env -f docker-compose.prod.yml ps
docker compose --env-file .env -f docker-compose.prod.yml logs -f --tail 100 app     # or caddy, frontend, postgres, minio
```
Logs rotate (3 files of 10 MB per container). `/actuator/*` is not reachable from the internet; check health on the server with
`docker inspect -f '{{.State.Health.Status}}' connecta-app`.

## 7. Meta and other manual settings
- Webhook callback URL: `https://<API_DOMAIN>/api/meta/webhooks`, verify token = `META_WEBHOOK_VERIFY_TOKEN`.
- OAuth redirect URI: `https://<API_DOMAIN>/api/meta/oauth/callback`.
- Behind Caddy the backend trusts `X-Forwarded-*` (`forward-headers-strategy: framework`), so sessions record the client IP.

## Backups
`vps-bootstrap.sh` installs a daily cron job that runs `scripts/backup-postgres.sh` (copied here by the deploy workflow). Retention,
off-server copy, MinIO and the restore drill are in [operations.md](operations.md).
