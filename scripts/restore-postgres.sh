#!/usr/bin/env bash
# Restores a dump made by backup-postgres.sh into a database inside the postgres container.
#   ./restore-postgres.sh <dump-file> [target-database]
# The default target is "<POSTGRES_DB>_restore_test", a scratch database for a restore drill: it is dropped and
# recreated. To restore over the live database, name it AND pass --force-production (stop the app first:
# docker compose --env-file .env -f docker-compose.prod.yml stop app frontend).
set -euo pipefail
cd "$(dirname "$0")"

FORCE=0
ARGS=()
for a in "$@"; do
  if [ "$a" = "--force-production" ]; then FORCE=1; else ARGS+=("$a"); fi
done
FILE="${ARGS[0]:?usage: restore-postgres.sh <dump-file> [target-database] [--force-production]}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
CONTAINER="${POSTGRES_CONTAINER:-connecta-postgres}"
PROD_DB="${POSTGRES_DB:?POSTGRES_DB not set}"
DB_USER="${POSTGRES_USER:?POSTGRES_USER not set}"
TARGET="${ARGS[1]:-${PROD_DB}_restore_test}"

if [ "$TARGET" = "$PROD_DB" ] && [ "$FORCE" -ne 1 ]; then
  echo "Refusing to overwrite the production database '$PROD_DB'. Pass --force-production if you mean it." >&2
  exit 2
fi

echo "Restoring $FILE into database '$TARGET'"
docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)" -c "CREATE DATABASE \"$TARGET\""
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$TARGET" --no-owner --exit-on-error < "$FILE"
echo "Restored. Check it, e.g.: docker exec $CONTAINER psql -U $DB_USER -d $TARGET -c 'select count(*) from users'"
