#!/usr/bin/env bash
# Deploys one image tag on the VPS. The GitHub workflow runs it over SSH; you can run it by hand too:
#   cd /opt/connecta && ./deploy-remote.sh <tag>        (tag = commit SHA from GHCR, or "latest")
# Expects next to it: docker-compose.prod.yml, Caddyfile, .env (+ docker-compose.monitoring.yml when MONITORING_ENABLED=true) (see deploy/.env.production.example), and a
# docker login to ghcr.io when the images are private.
# Flow: pull -> up -d -> wait for the backend to be healthy -> remember the tag. On a failed health check it
# starts the previous tag again and exits 1. The first deploy has no previous tag, so it only reports failure.
set -euo pipefail

TAG="${1:?usage: deploy-remote.sh <image-tag>}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}" # seconds to wait for the backend (Flyway runs on start)
cd "$(dirname "$0")"

STATE_FILE=.last-good-tag
COMPOSE=(docker compose --env-file .env -f docker-compose.prod.yml)
# Monitoring (Prometheus, Grafana, node-exporter) is an optional second compose file, switched on in .env.
if [ -f docker-compose.monitoring.yml ] && grep -qx 'MONITORING_ENABLED=true' .env; then
  COMPOSE+=(-f docker-compose.monitoring.yml)
fi

PREVIOUS=""
[ -f "$STATE_FILE" ] && PREVIOUS="$(cat "$STATE_FILE")"

wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) status
  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' connecta-app 2>/dev/null || echo missing)"
    [ "$status" = healthy ] && return 0
    sleep 5
  done
  return 1
}

start() {
  IMAGE_TAG="$1" "${COMPOSE[@]}" pull
  IMAGE_TAG="$1" "${COMPOSE[@]}" up -d --remove-orphans
}

echo "Deploying tag $TAG (previous: ${PREVIOUS:-none})"
start "$TAG"

if wait_healthy; then
  echo "$TAG" > "$STATE_FILE"
  docker image prune -f >/dev/null
  echo "Deployed $TAG, backend is healthy."
  exit 0
fi

echo "Backend did not become healthy within ${HEALTH_TIMEOUT}s. Last log lines:" >&2
docker logs --tail 40 connecta-app >&2 || true

if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$TAG" ]; then
  echo "Rolling back to $PREVIOUS" >&2
  start "$PREVIOUS"
  wait_healthy && echo "Rolled back to $PREVIOUS, backend is healthy." >&2 \
    || echo "Rollback to $PREVIOUS is not healthy either, check the logs." >&2
else
  echo "No previous tag to roll back to." >&2
fi
exit 1
