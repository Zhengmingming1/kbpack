#!/usr/bin/env bash
set -euo pipefail

umask 077

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-$PROJECT_DIR/docker-compose.yml}
ENV_FILE=${ENV_FILE:-$PROJECT_DIR/.env}
BACKUP_ROOT=${BACKUP_ROOT:-$PROJECT_DIR/backup}
BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-7}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)

die() {
    printf 'backup: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

compose() {
    docker compose \
        --project-directory "$PROJECT_DIR" \
        --env-file "$ENV_FILE" \
        -f "$COMPOSE_FILE" \
        "$@"
}

require_command docker
require_command gzip
require_command tar

[[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"
[[ -f "$ENV_FILE" ]] || die "environment file not found: $ENV_FILE"
[[ "$BACKUP_RETENTION_DAYS" =~ ^[0-9]+$ ]] || die "BACKUP_RETENTION_DAYS must be an integer"

if [[ "$BACKUP_ROOT" != /* ]]; then
    BACKUP_ROOT=$PROJECT_DIR/$BACKUP_ROOT
fi
BACKUP_ROOT=$(mkdir -p "$BACKUP_ROOT" && CDPATH= cd -- "$BACKUP_ROOT" && pwd)
[[ "$BACKUP_ROOT" != / ]] || die "refusing to use / as BACKUP_ROOT"

POSTGRES_DIR=$BACKUP_ROOT/postgres
MINIO_DIR=$BACKUP_ROOT/minio
CONFIG_DIR=$BACKUP_ROOT/config
MANIFEST_DIR=$BACKUP_ROOT/manifests
LOCK_DIR=$BACKUP_ROOT/.backup.lock

mkdir -p "$POSTGRES_DIR" "$MINIO_DIR" "$CONFIG_DIR" "$MANIFEST_DIR"
mkdir "$LOCK_DIR" 2>/dev/null || die "another backup appears to be running: $LOCK_DIR"

POSTGRES_TMP=$POSTGRES_DIR/.kbpack-$STAMP.sql.gz.tmp
POSTGRES_FINAL=$POSTGRES_DIR/kbpack-$STAMP.sql.gz
MINIO_TMP=$MINIO_DIR/.tmp-$STAMP
MINIO_FINAL=$MINIO_DIR/$STAMP
CONFIG_TMP=$CONFIG_DIR/.kbpack-config-$STAMP.tar.gz.tmp
CONFIG_FINAL=$CONFIG_DIR/kbpack-config-$STAMP.tar.gz
MANIFEST_TMP=$MANIFEST_DIR/.$STAMP.complete.tmp
MANIFEST_FINAL=$MANIFEST_DIR/$STAMP.complete

cleanup() {
    status=$?
    trap - EXIT HUP INT TERM
    [[ -n "${POSTGRES_TMP:-}" ]] && rm -f -- "$POSTGRES_TMP"
    [[ -n "${CONFIG_TMP:-}" ]] && rm -f -- "$CONFIG_TMP"
    [[ -n "${MANIFEST_TMP:-}" ]] && rm -f -- "$MANIFEST_TMP"
    [[ -n "${MINIO_TMP:-}" ]] && rm -rf -- "$MINIO_TMP"
    rmdir "$LOCK_DIR" 2>/dev/null || true
    exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

for path in "$POSTGRES_FINAL" "$MINIO_FINAL" "$CONFIG_FINAL" "$MANIFEST_FINAL"; do
    [[ ! -e "$path" ]] || die "backup target already exists: $path"
done

compose exec -T postgres sh -ec \
    'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null'
compose exec -T minio curl -fsS http://localhost:9000/minio/health/live >/dev/null

compose exec -T postgres sh -ec \
    'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges' \
    | gzip -9 > "$POSTGRES_TMP"
[[ -s "$POSTGRES_TMP" ]] || die "PostgreSQL dump is empty"

mkdir -p "$MINIO_TMP"
compose run --rm --no-deps \
    -v "$MINIO_TMP:/backup" \
    minio-client '
        set -eu
        mc alias set kbpack http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
        for bucket in kb-original kb-packages kb-derived; do
            mkdir -p "/backup/$bucket"
            mc mirror --overwrite "kbpack/$bucket" "/backup/$bucket"
        done
    '

tar -C "$PROJECT_DIR" -czf "$CONFIG_TMP" docker-compose.yml Caddyfile .env
[[ -s "$CONFIG_TMP" ]] || die "configuration archive is empty"

mv "$POSTGRES_TMP" "$POSTGRES_FINAL"
POSTGRES_TMP=
mv "$MINIO_TMP" "$MINIO_FINAL"
MINIO_TMP=
mv "$CONFIG_TMP" "$CONFIG_FINAL"
CONFIG_TMP=

{
    printf 'timestamp=%s\n' "$STAMP"
    printf 'postgres=postgres/%s\n' "$(basename -- "$POSTGRES_FINAL")"
    printf 'minio=minio/%s\n' "$STAMP"
    printf 'config=config/%s\n' "$(basename -- "$CONFIG_FINAL")"
} > "$MANIFEST_TMP"
mv "$MANIFEST_TMP" "$MANIFEST_FINAL"
MANIFEST_TMP=

while IFS= read -r -d '' manifest; do
    old_stamp=$(basename -- "$manifest" .complete)
    [[ "$old_stamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || continue
    rm -f -- \
        "$POSTGRES_DIR/kbpack-$old_stamp.sql.gz" \
        "$CONFIG_DIR/kbpack-config-$old_stamp.tar.gz" \
        "$manifest"
    rm -rf -- "$MINIO_DIR/$old_stamp"
done < <(find "$MANIFEST_DIR" -type f -name '*.complete' -mtime "+$BACKUP_RETENTION_DAYS" -print0)

printf 'Backup complete: %s\n' "$STAMP"
printf '  PostgreSQL: %s\n' "$POSTGRES_FINAL"
printf '  MinIO:      %s\n' "$MINIO_FINAL"
printf '  Config:     %s\n' "$CONFIG_FINAL"
