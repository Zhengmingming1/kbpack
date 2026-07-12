#!/usr/bin/env bash
set -euo pipefail

umask 077

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-$PROJECT_DIR/docker-compose.yml}
ENV_FILE=${ENV_FILE:-$PROJECT_DIR/.env}
BACKUP_ROOT=${BACKUP_ROOT:-$PROJECT_DIR/backup}

ASSUME_YES=false
RESTORE_CONFIG=false
SKIP_MINIO=false
TARGET=latest

usage() {
    cat <<'USAGE'
Usage: restore.sh [options] [latest|TIMESTAMP|PATH_TO_SQL_GZ]

Options:
  --yes             Skip the destructive-operation confirmation.
  --restore-config  Restore docker-compose.yml, Caddyfile, and .env.
  --skip-minio      Restore PostgreSQL only.
  -h, --help        Show this help.
USAGE
}

die() {
    printf 'restore: %s\n' "$*" >&2
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

wait_for_postgres() {
    local attempt
    for ((attempt = 1; attempt <= 60; attempt++)); do
        if compose exec -T postgres sh -ec \
            'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null' 2>/dev/null; then
            return 0
        fi
        sleep 2
    done
    return 1
}

wait_for_minio() {
    local attempt
    for ((attempt = 1; attempt <= 60; attempt++)); do
        if compose exec -T minio curl -fsS http://localhost:9000/minio/health/live >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

wait_for_app() {
    local attempt
    for ((attempt = 1; attempt <= 60; attempt++)); do
        if compose exec -T app curl -fsS http://localhost:18080/health >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

while (($# > 0)); do
    case "$1" in
        --yes)
            ASSUME_YES=true
            ;;
        --restore-config)
            RESTORE_CONFIG=true
            ;;
        --skip-minio)
            SKIP_MINIO=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --*)
            die "unknown option: $1"
            ;;
        *)
            [[ "$TARGET" == latest ]] || die "only one backup target may be specified"
            TARGET=$1
            ;;
    esac
    shift
done

require_command docker
require_command gzip
require_command tar

[[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"
[[ -f "$ENV_FILE" ]] || die "environment file not found: $ENV_FILE"

if [[ "$BACKUP_ROOT" != /* ]]; then
    BACKUP_ROOT=$PROJECT_DIR/$BACKUP_ROOT
fi
BACKUP_ROOT=$(CDPATH= cd -- "$BACKUP_ROOT" 2>/dev/null && pwd) \
    || die "backup root not found: $BACKUP_ROOT"
MANIFEST_DIR=$BACKUP_ROOT/manifests
[[ -d "$MANIFEST_DIR" ]] || die "backup manifest directory not found: $MANIFEST_DIR"

EXPLICIT_POSTGRES_BACKUP=

if [[ "$TARGET" == latest ]]; then
    MANIFEST=$(find "$MANIFEST_DIR" -type f -name '*.complete' -print 2>/dev/null | sort | tail -n 1)
    [[ -n "$MANIFEST" ]] || die "no completed backups found under $MANIFEST_DIR"
    STAMP=$(basename -- "$MANIFEST" .complete)
elif [[ -f "$TARGET" ]]; then
    base=$(basename -- "$TARGET")
    [[ "$base" =~ ^kbpack-([0-9]{8}T[0-9]{6}Z)\.sql\.gz$ ]] \
        || die "cannot derive timestamp from backup filename: $base"
    STAMP=${BASH_REMATCH[1]}
    MANIFEST=$MANIFEST_DIR/$STAMP.complete
    EXPLICIT_POSTGRES_BACKUP=$(CDPATH= cd -- "$(dirname -- "$TARGET")" && pwd)/$base
else
    [[ "$TARGET" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "invalid backup timestamp: $TARGET"
    STAMP=$TARGET
    MANIFEST=$MANIFEST_DIR/$STAMP.complete
fi

POSTGRES_BACKUP=${EXPLICIT_POSTGRES_BACKUP:-$BACKUP_ROOT/postgres/kbpack-$STAMP.sql.gz}
MINIO_BACKUP=$BACKUP_ROOT/minio/$STAMP
CONFIG_BACKUP=$BACKUP_ROOT/config/kbpack-config-$STAMP.tar.gz

[[ -f "$MANIFEST" ]] || die "completion manifest not found: $MANIFEST"
[[ -s "$POSTGRES_BACKUP" ]] || die "PostgreSQL backup not found: $POSTGRES_BACKUP"
if [[ "$SKIP_MINIO" == false ]]; then
    [[ -d "$MINIO_BACKUP" ]] || die "MinIO backup not found: $MINIO_BACKUP"
fi
if [[ "$RESTORE_CONFIG" == true ]]; then
    [[ -s "$CONFIG_BACKUP" ]] || die "configuration backup not found: $CONFIG_BACKUP"
fi

if [[ "$ASSUME_YES" == false ]]; then
    [[ -t 0 ]] || die "confirmation requires a terminal; pass --yes for automation"
    printf 'This will replace the live kbpack database'
    [[ "$SKIP_MINIO" == true ]] || printf ' and MinIO objects'
    printf ' with backup %s.\n' "$STAMP"
    printf 'Type RESTORE %s to continue: ' "$STAMP"
    IFS= read -r confirmation
    [[ "$confirmation" == "RESTORE $STAMP" ]] || die "confirmation did not match"
fi

if [[ "$RESTORE_CONFIG" == true ]]; then
    while IFS= read -r entry; do
        case "$entry" in
            docker-compose.yml|Caddyfile|.env)
                ;;
            *)
                die "unexpected path in configuration archive: $entry"
                ;;
        esac
    done < <(tar -tzf "$CONFIG_BACKUP")
    tar -C "$PROJECT_DIR" -xzf "$CONFIG_BACKUP"
fi

printf 'Stopping application services...\n'
compose stop caddy web app meilisearch >/dev/null

printf 'Starting storage services...\n'
compose up -d postgres minio >/dev/null
wait_for_postgres || die "PostgreSQL did not become ready"
wait_for_minio || die "MinIO did not become ready"

printf 'Restoring PostgreSQL...\n'
gzip -dc "$POSTGRES_BACKUP" \
    | compose exec -T postgres sh -ec \
        'exec psql --set ON_ERROR_STOP=on --single-transaction --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --file=-'

if [[ "$SKIP_MINIO" == false ]]; then
    printf 'Restoring MinIO buckets...\n'
    compose run --rm --no-deps \
        -v "$MINIO_BACKUP:/backup:ro" \
        minio-client '
            set -eu
            mc alias set kbpack http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
            for bucket in kb-original kb-packages kb-derived kb-backup; do
                mc mb --ignore-existing "kbpack/$bucket" >/dev/null
                mc mirror --overwrite --remove "/backup/$bucket" "kbpack/$bucket"
            done
        '
fi

printf 'Starting application services...\n'
compose up -d meilisearch app web caddy >/dev/null
wait_for_app || die "application did not become healthy after restore"

printf 'Restore complete: %s\n' "$STAMP"
printf 'Run the authenticated POST /api/v1/search/reindex endpoint after login.\n'
