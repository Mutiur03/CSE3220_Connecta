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

Files: `deploy/docker-compose.prod.yml`, `deploy/Caddyfile`, `deploy/.env.production.example`,
`.github/workflows/ci.yml` and the optional `deploy/docker-compose.monitoring.yml`.
Running the server afterwards (logs, monitoring, secrets, disk, runbook): [operations.md](operations.md).

## What is manual (nothing here is done by CI)
- Renting the VPS, the domain and DNS records.
- Preparing the server (prerequisites below) and writing the `.env` file on it.
- Adding the GitHub secrets and variables below.
- Registering the Meta webhook and OAuth redirect URLs with the production hostnames.
- Writing `/opt/connecta/.env` on the server. It is the only file you put there; the deploy copies the compose file and Caddyfile.
- Optional monitoring, see [operations.md](operations.md). Database backups are not part of this setup.

Not verified yet: the whole chain was written without a real VPS. Expect to fix small things on the first real deploy.

## 1. DNS
Three records (A, plus AAAA if the VPS has IPv6) to the VPS address. Names are yours; these are examples:

| Name | Serves |
|---|---|
| `app.example.com` | web app (`APP_DOMAIN`) |
| `api.example.com` | backend API, WebSocket and Meta webhook (`API_DOMAIN`) |
| `cdn.example.com` | product images from MinIO (`FILES_DOMAIN`; the name is only a convention) |

Cloudflare proxying (orange cloud) in front of `cdn.<domain>` is an optional later step for caching; start with plain DNS so Caddy can get its certificate.

DNS must resolve before the first start, otherwise Caddy cannot get certificates (it retries, so it recovers once DNS is right).

## 2. Server prerequisites (done by you)
- Ubuntu 22.04 or 24.04 with Docker and the compose plugin (`docker compose version` works).
- The deploy runs as `root` (secret `SSH_USER=root`), with the GitHub Actions public key in `/root/.ssh/authorized_keys`. Make a key pair used
  only for this on your machine: `ssh-keygen -t ed25519 -f connecta-deploy -C github-actions`. The key has full control of the server:
  keep SSH key-only (`PermitRootLogin prohibit-password`) and pin the host key with the `VPS_KNOWN_HOSTS` secret.
- Ports 22, 80 and 443 (tcp, and 443 udp for HTTP/3) open in the firewall. Nothing else needs to be public.
- Confirm `ssh -i connecta-deploy root@<host>` works. The deploy creates `/opt/connecta` (or your `VPS_DIR`) itself.

Then put the env file on the server (the only file you place by hand; repeat it only when a setting changes):
   ```bash
   ssh root@<host> "mkdir -p /opt/connecta"
   scp deploy/.env.production.example root@<host>:/opt/connecta/.env
   ssh root@<host> "chmod 600 /opt/connecta/.env && nano /opt/connecta/.env"
   ```
   Replace every `CHANGE` value and the three domains. Generate secrets with `openssl rand -base64 48` (JWT_SECRET),
   `openssl rand -base64 32` (META_TOKEN_KEY) and `openssl rand -base64 24` (database and MinIO passwords).
   Every variable is documented inside the file.

## 3. GitHub configuration
Settings -> Secrets and variables -> Actions.

| Kind | Name | Value |
|---|---|---|
| Secret | `SSH_HOST` | server address or hostname |
| Secret | `SSH_USER` | `root` |
| Secret | `SSH_PRIVATE_KEY` | contents of the private key `connecta-deploy` |
| Secret (optional) | `VPS_PORT` | SSH port if not 22 |
| Secret (optional) | `VPS_KNOWN_HOSTS` | output of `ssh-keyscan -p <port> <host>`; without it the workflow trusts the key it sees on each run |
| Variable | `NEXT_PUBLIC_API_URL` | `https://api.example.com` (baked into the frontend image at build time) |
| Variable (optional) | `VPS_DIR` | install directory, default `/opt/connecta` |

If a required secret or variable is missing, the workflow still goes green: the image build and deploy are skipped with a notice
that names what is missing.

Also set Settings -> Actions -> General -> Workflow permissions to "Read and write" (the workflow asks for `packages: write` itself).
The images are private by default in GHCR; the workflow logs the server in with the run's token for each deploy.

## 4. Deploy
Merge to `main` (the release PR). The `deploy` jobs of `ci.yml` then run, only after the backend and frontend tests pass:
1. the backend and frontend tests (the same jobs every PR runs);
2. builds `connecta-backend` and `connecta-frontend`, tags them with the commit SHA and `latest`, and pushes to
   `ghcr.io/<owner>/` (Docker layer cache in the Actions cache);
3. over SSH, in `/opt/connecta` on the server: copies `docker-compose.prod.yml` and `Caddyfile` from that commit, logs the server
   in to GHCR with the run's token, then `docker compose pull` and `docker compose up -d` for that commit's tag. Nothing else is
   changed on the server. Flyway migrations run when the backend starts; check with `docker compose ps` and the logs.

First deploy: the database starts empty. Open `https://<APP_DOMAIN>/setup` to create the owner account.

## 5. Roll back
There is no automatic rollback. Flyway migrations are not reverted: write migrations so the previous image still works
with the new schema (additive changes first).

To go back to an older version, merge a revert of the bad change into `main`; the push deploys it like any other.

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
