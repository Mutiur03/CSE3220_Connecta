# Operations runbook (VPS)

Setup and first deploy are in [deployment.md](deployment.md). This page is for running the server afterwards. Nothing here
has been run on a real VPS yet; the commands are written against `deploy/docker-compose.prod.yml` and may need small fixes.

All commands run on the server in `/opt/connecta` as the `deploy` user. Shorthand used below:

```bash
cd /opt/connecta
DC="docker compose --env-file .env -f docker-compose.prod.yml"
# with monitoring enabled add:  -f docker-compose.monitoring.yml
```

## Health and status
| Check | How |
|---|---|
| All containers | `$DC ps` |
| Backend | `docker inspect -f '{{.State.Health.Status}}' connecta-app` or `docker exec connecta-app wget -qO- http://127.0.0.1:8080/actuator/health` (`{"status":"UP"}`) |
| Database | `docker exec connecta-postgres pg_isready -U "$POSTGRES_USER"` |
| Public site | `curl -fsS https://<APP_DOMAIN>/` and `curl -fsS https://<API_DOMAIN>/api/...` |
| Actuator is private | `curl -s -o /dev/null -w '%{http_code}' https://<API_DOMAIN>/actuator/health` must print `404` (Caddy blocks `/actuator`) |

## Logs
Every service logs to the Docker `json-file` driver with rotation (3 files of 10 MB per container).
```bash
$DC logs -f --tail 100 app            # one service: app, frontend, caddy, postgres, minio, prometheus, grafana
$DC logs --since 30m app | grep -i error
docker logs --since 1h connecta-app 2>&1 | grep ERROR
```
The backend writes one JSON object per line (Spring Boot structured logging, `LOG_FORMAT=logstash`, set in the compose file), so
it can be filtered with `jq`:
```bash
$DC logs --no-log-prefix app | jq -c 'select(.level=="ERROR") | {"@timestamp", logger_name, message}'
```
Set `LOG_FORMAT=ecs` in `.env` for the ECS dialect. There is no request or trace id in the log lines yet (no MDC filter exists);
correlate by time and logger name. Logs must carry no personal data or secrets; report any line that does.

## Monitoring
Optional stack in `deploy/docker-compose.monitoring.yml`: Prometheus (15 days or 2 GB of data), Grafana and node-exporter,
all on the internal network, data on the named volumes `prometheus-data` and `grafana-data`.

Turn it on: in `.env` set `MONITORING_ENABLED=true`, `METRICS_SCRAPE_TOKEN` (`openssl rand -base64 32`) and
`GRAFANA_ADMIN_PASSWORD`, then run `./deploy-remote.sh <tag>` (or push to `main`). `deploy-remote.sh` adds the second compose
file only when that flag is `true`. Turn it off by setting it to `false` and removing the containers:
`docker rm -f connecta-prometheus connecta-grafana connecta-node-exporter` (data volumes stay).

How the backend is scraped: Prometheus calls `app:8080/actuator/prometheus` over the compose network with the header
`X-Scrape-Token: <METRICS_SCRAPE_TOKEN>`. The backend accepts that shared secret for this one endpoint only (an ADMIN access
token also works). Caddy answers 404 for `/actuator`, so the endpoint is not public. A blank `METRICS_SCRAPE_TOKEN` disables the token.

Grafana access, safest default: it listens on `127.0.0.1:3001` of the server only. Open an SSH tunnel and browse locally:
```bash
ssh -L 3001:127.0.0.1:3001 deploy@<host>     # then http://localhost:3001, user GRAFANA_ADMIN_USER
```
Optional public access (not enabled): add a site block to `deploy/Caddyfile`, set `GRAFANA_DOMAIN` in the caddy service
environment, point DNS at the server, and put basic auth in front of Grafana's own login:
```
{$GRAFANA_DOMAIN} {
	import security_headers
	basic_auth {
		ops <output of: docker run --rm caddy:2.8-alpine caddy hash-password>
	}
	reverse_proxy grafana:3000
}
```

### Dashboard tour ("Connecta Backend", folder Connecta)
| Panel | Reads as |
|---|---|
| JVM heap used | Memory the backend holds. A steady climb that never drops after garbage collection means a leak or too small a heap. |
| HTTP request rate | Requests per second. A drop to zero with the site up means traffic is not reaching the backend. |
| HTTP p95 latency | 95 percent of requests are faster than this. Rising latency with a flat request rate usually means the database. |
| HTTP 5xx rate | Server errors per second. Should be flat zero. |
| DB pool connections | Active connections against the pool maximum. Active touching the maximum means requests wait for the database. |

The dashboard JSON is `ops/grafana/connecta-dashboard.json`, provisioned read-only from `ops/grafana/provisioning/` (datasource
uid `prometheus`). Edit the JSON in git, not in the UI.

### Alerts
Rules live in `ops/prometheus/alerts.yml`. There is no Alertmanager, so nothing is emailed or paged: they show in the Prometheus UI
(`docker exec -it connecta-prometheus wget -qO- http://127.0.0.1:9090/api/v1/alerts` on the server) and in Grafana under Alerting,
state from the Prometheus datasource. Wire an Alertmanager if someone must be woken up.

| Alert | Fires when | First step |
|---|---|---|
| BackendDown | the backend cannot be scraped for 2 min | `$DC ps`, `$DC logs --tail 200 app`; see "Backend unhealthy". A wrong `METRICS_SCRAPE_TOKEN` looks the same |
| HighServerErrorRate | over 5 percent of requests are 5xx for 5 min | read the `ERROR` log lines of `app`; roll back if it began with a deploy |
| DbPoolSaturated | over 90 percent of the pool in use for 5 min | look for slow queries (`docker exec connecta-postgres psql -U ... -c "select * from pg_stat_activity"`), restart `app` if connections leak |
| DiskAlmostFull | root disk over 85 percent for 10 min | see "Disk full" |

Host metrics come from node-exporter (CPU, memory, disk). It runs on the compose network, so network-interface metrics are the
container's, not the host's.

## Deploy and roll back
Deploy: merge to `main` (see deployment.md section 4) or by hand `./deploy-remote.sh <sha>`.
Roll back: `./deploy-remote.sh <older-sha>` (tags in GHCR; `cat .last-good-tag` is the last healthy one). Automatic rollback happens
when the new backend is not healthy within 240 s.

Flyway caveat: migrations only go forward. A rollback starts the old image against the already migrated database. That works only if
the new migration was additive (new nullable column, new table). A migration that dropped or renamed something cannot be rolled back
by swapping images: fix forward with a new migration, or restore the database from a backup taken before the deploy (below), which
loses data written since. Take a manual backup (`./backup-postgres.sh`) before deploying a risky migration.

## Restart one service
```bash
$DC restart app              # backend only (frontend and caddy keep running)
$DC up -d --force-recreate app   # also re-reads .env changes
```

## Secrets
Edit `.env` (chmod 600), then `$DC up -d` so containers pick it up.
- `JWT_SECRET`: changing it invalidates every access token at once; refresh tokens are stored hashed in the database, so users stay
  signed in after the next refresh. Access tokens live 15 s in production settings, so the effect is brief.
- `META_TOKEN_KEY`: do not rotate casually. Stored Meta tokens are encrypted with it; a new key makes the existing Meta connections
  unreadable and each organization must reconnect.
- `POSTGRES_PASSWORD`: also change it inside the database or the app cannot connect:
  `docker exec connecta-postgres psql -U "$POSTGRES_USER" -c "ALTER USER \"$POSTGRES_USER\" PASSWORD 'new'"`, then update `.env` and recreate `app`.
- `MINIO_ROOT_*`, `METRICS_SCRAPE_TOKEN`, `GRAFANA_ADMIN_PASSWORD`: edit `.env`, recreate the affected services. The Grafana password
  is only read on first start; change it later with `docker exec connecta-grafana grafana cli admin reset-admin-password <new>`.

## Certificates
Caddy renews them itself (certificates and account live in the `caddy-data` volume; do not delete it, Let's Encrypt rate limits apply).
Check: `$DC logs caddy | grep -i -E "certificate|obtain|renew"`. If a renewal fails, the usual cause is DNS no longer pointing at the
server or ports 80/443 blocked: fix that, then `$DC restart caddy`.

## Disk full
```bash
df -h / ; docker system df
docker image prune -af          # old images (deploys already prune dangling ones)
docker builder prune -af
journalctl --vacuum-size=200M
du -sh /var/lib/docker/volumes/* | sort -h | tail
```
Container logs are capped (30 MB per container). Backups in `BACKUP_DIR` are the usual culprit after the databases; lower
`BACKUP_RETENTION_DAYS`. Never `docker volume prune` while a service is stopped: it deletes that service's data.

## Backend unhealthy
1. `$DC ps` and `$DC logs --tail 200 app`.
2. Database down or still starting: `docker inspect -f '{{.State.Health.Status}}' connecta-postgres`.
3. Flyway failure on start shows as a migration error in the log. Do not edit the failed migration; roll back the image
   (`./deploy-remote.sh <older-sha>`) if the schema is unchanged, otherwise fix forward.
4. Out of memory: `docker stats --no-stream`, `dmesg | grep -i oom`.
5. `$DC restart app` as a last step, not the first.

## Backups and restore drill
Database: `scripts/backup-postgres.sh` runs `pg_dump -Fc` inside `connecta-postgres`, writes
`$BACKUP_DIR/<db>-<UTC timestamp>.dump` (mode 700 directory), deletes dumps older than `BACKUP_RETENTION_DAYS` (default 14) and exits
non-zero on any failure. `vps-bootstrap.sh` installs `/etc/cron.d/connecta-backup` (daily 02:30, log in `/opt/connecta/backup.log`);
a systemd timer works equally well. Off-server copy (a backup on the same disk is not a backup): set `BACKUP_RSYNC_TARGET`
(`user@host:/dir`) or `BACKUP_MC_TARGET` (an `mc` alias path such as `offsite/connecta-backups`; install the `mc` client and run
`mc alias set offsite ...` as the deploy user first). If the off-server copy fails the script exits non-zero after the local dump exists.

Restore drill (do it once now and after big changes), into a scratch database, production untouched:
```bash
./restore-postgres.sh backups/connecta-<timestamp>.dump        # creates connecta_restore_test
docker exec connecta-postgres psql -U "$POSTGRES_USER" -d connecta_restore_test -c 'select count(*) from organizations'
docker exec connecta-postgres psql -U "$POSTGRES_USER" -d postgres -c 'DROP DATABASE connecta_restore_test'
```
Real recovery over the live database: stop `app` and `frontend`, run
`./restore-postgres.sh backups/<file> "$POSTGRES_DB" --force-production`, start them again. Without `--force-production` the script
refuses to touch the production database name.

MinIO (product images, volume `minio-data`) is not covered by `pg_dump`. Copy it with the mc client, for example
`docker run --rm --network connecta_connecta -v /opt/connecta/backups/minio:/out --entrypoint sh minio/mc:RELEASE.2024-11-21T17-21-54Z -c 'mc alias set l http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" && mc mirror l/$S3_BUCKET /out'`
(pass the three variables with `--env-file .env`; the network name is `<compose project>_connecta`, check `docker network ls`),
or snapshot the VPS. Also keep a copy of `.env` somewhere safe: without `META_TOKEN_KEY` the Meta connections are lost.
