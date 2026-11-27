#!/usr/bin/env bash
# Dumps the production database (pg_dump custom format, runs inside the postgres container), keeps the last
# BACKUP_RETENTION_DAYS days, and optionally copies the dump off the server. Exits non-zero on any failure.
#   /opt/connecta/backup-postgres.sh            (cron or the systemd timer calls it, see docs/operations.md)
# Settings come from the environment or from .env next to this script: POSTGRES_DB, POSTGRES_USER,
# BACKUP_DIR (default ./backups), BACKUP_RETENTION_DAYS (default 14), BACKUP_RSYNC_TARGET (user@host:/dir),
# BACKUP_MC_TARGET (mc alias/bucket/dir; needs the mc client and an alias set up by the deploy user).
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

CONTAINER="${POSTGRES_CONTAINER:-connecta-postgres}"
DB="${POSTGRES_DB:?POSTGRES_DB not set}"
DB_USER="${POSTGRES_USER:?POSTGRES_USER not set}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION="${BACKUP_RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
FILE="$BACKUP_DIR/${DB}-$(date -u +%Y%m%dT%H%M%SZ).dump"

# Write to a temp name first so a half-written dump is never mistaken for a backup.
if ! docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" -Fc > "$FILE.part"; then
  rm -f "$FILE.part"
  echo "pg_dump failed" >&2
  exit 1
fi
[ -s "$FILE.part" ] || { rm -f "$FILE.part"; echo "pg_dump produced an empty file" >&2; exit 1; }
mv "$FILE.part" "$FILE"
echo "Backup written: $FILE ($(du -h "$FILE" | cut -f1))"

find "$BACKUP_DIR" -maxdepth 1 -name "${DB}-*.dump" -type f -mtime "+$RETENTION" -print -delete

if [ -n "${BACKUP_RSYNC_TARGET:-}" ]; then
  rsync -a "$FILE" "$BACKUP_RSYNC_TARGET/"
  echo "Copied to $BACKUP_RSYNC_TARGET"
fi
if [ -n "${BACKUP_MC_TARGET:-}" ]; then
  mc cp "$FILE" "$BACKUP_MC_TARGET/"
  echo "Copied to $BACKUP_MC_TARGET"
fi
