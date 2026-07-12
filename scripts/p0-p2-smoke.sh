#!/usr/bin/env bash
# Smoke test for P0–P2 against a running local backend.
set -euo pipefail

BASE="${1:-http://localhost:18080}"
BASE="${BASE%/}"
USERNAME=admin

die() {
  printf 'smoke: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_command curl
require_command node

TMP_DIR="$(mktemp -d)"
COOKIE_JAR="$TMP_DIR/cookies"
HEALTH_JSON="$TMP_DIR/health.json"
LOGIN_JSON="$TMP_DIR/login.json"
ME_JSON="$TMP_DIR/me.json"
STATS_JSON="$TMP_DIR/stats.json"
PACKAGES_JSON="$TMP_DIR/packages.json"

cleanup() {
  rm -f -- "$COOKIE_JAR" "$HEALTH_JSON" "$LOGIN_JSON" "$ME_JSON" "$STATS_JSON" "$PACKAGES_JSON"
  rmdir -- "$TMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

request() {
  local label=$1
  local output=$2
  local http_status
  shift 2

  printf '== %s ==\n' "$label"
  if ! http_status=$(curl --silent --show-error --output "$output" --write-out '%{http_code}' "$@"); then
    die "$label request failed"
  fi
  cat "$output"
  printf '\n'

  case "$http_status" in
    2??) ;;
    *) die "$label returned HTTP $http_status" ;;
  esac
}

assert_json() {
  local kind=$1
  local input=$2

  node - "$kind" "$input" "$USERNAME" <<'NODE' || die "$kind response failed validation"
const fs = require('fs');

const [kind, input, expectedUsername] = process.argv.slice(2);
let body;

try {
  body = JSON.parse(fs.readFileSync(input, 'utf8'));
} catch (error) {
  console.error(`smoke: ${kind} returned invalid JSON: ${error.message}`);
  process.exit(1);
}

function check(condition, message) {
  if (!condition) {
    console.error(`smoke: ${kind} ${message}`);
    process.exit(1);
  }
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0;
}

function checkUser(user) {
  check(isObject(user), 'must contain a user object');
  check(typeof user.id === 'string' && user.id.length > 0, 'user.id must be a non-empty string');
  check(user.username === expectedUsername, `user.username must equal ${expectedUsername}`);
  check(typeof user.display_name === 'string', 'user.display_name must be a string');
  check(typeof user.role === 'string' && user.role.length > 0, 'user.role must be a non-empty string');
}

check(isObject(body), 'response must be a JSON object');

switch (kind) {
  case 'health':
    check(body.status === 'up', 'status must equal up');
    break;
  case 'login':
    checkUser(body.user);
    break;
  case 'me':
    checkUser(body);
    break;
  case 'stats':
    for (const field of ['package_count', 'document_count', 'storage_used_bytes', 'parse_failed_count']) {
      check(isNonNegativeInteger(body[field]), `${field} must be a non-negative integer`);
    }
    check(Array.isArray(body.recent_uploads), 'recent_uploads must be an array');
    for (const upload of body.recent_uploads) {
      check(isObject(upload), 'recent_uploads entries must be objects');
      check(typeof upload.package_id === 'string' && upload.package_id.length > 0,
        'recent_uploads[].package_id must be a non-empty string');
      check(typeof upload.title === 'string', 'recent_uploads[].title must be a string');
      check(typeof upload.created_at === 'string' && upload.created_at.length > 0,
        'recent_uploads[].created_at must be a non-empty string');
    }
    break;
  case 'packages':
    check(isNonNegativeInteger(body.total), 'total must be a non-negative integer');
    check(body.page === 1, 'page must equal 1');
    check(body.page_size === 5, 'page_size must equal 5');
    check(Array.isArray(body.items), 'items must be an array');
    for (const item of body.items) {
      check(isObject(item), 'items entries must be objects');
      check(typeof item.id === 'string' && item.id.length > 0, 'items[].id must be a non-empty string');
      check(typeof item.title === 'string', 'items[].title must be a string');
      check(typeof item.status === 'string' && item.status.length > 0,
        'items[].status must be a non-empty string');
      check(typeof item.visibility === 'string' && item.visibility.length > 0,
        'items[].visibility must be a non-empty string');
      check(Array.isArray(item.tags), 'items[].tags must be an array');
    }
    break;
  default:
    check(false, `has no validator for ${kind}`);
}
NODE
}

request health "$HEALTH_JSON" "$BASE/health"
assert_json health "$HEALTH_JSON"

request login "$LOGIN_JSON" \
  --cookie-jar "$COOKIE_JAR" \
  --header 'Content-Type: application/json' \
  --data '{"username":"admin","password":"admin123456"}' \
  "$BASE/api/v1/auth/login"
assert_json login "$LOGIN_JSON"

request me "$ME_JSON" --cookie "$COOKIE_JAR" "$BASE/api/v1/auth/me"
assert_json me "$ME_JSON"

request stats "$STATS_JSON" --cookie "$COOKIE_JAR" "$BASE/api/v1/stats"
assert_json stats "$STATS_JSON"

request packages "$PACKAGES_JSON" --cookie "$COOKIE_JAR" \
  "$BASE/api/v1/packages?page=1&page_size=5"
assert_json packages "$PACKAGES_JSON"

echo "OK"
