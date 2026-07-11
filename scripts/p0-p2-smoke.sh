#!/usr/bin/env bash
# Smoke test for P0–P2 against a running local backend.
set -euo pipefail
BASE="${1:-http://localhost:18080}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "== health =="
curl -sS "$BASE/health" | tee /tmp/kbpack-health.json
echo

echo "== login =="
curl -sS -c "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123456"}' \
  "$BASE/api/v1/auth/login" | tee /tmp/kbpack-login.json
echo

echo "== me =="
curl -sS -b "$COOKIE_JAR" "$BASE/api/v1/auth/me" | tee /tmp/kbpack-me.json
echo

echo "== stats =="
curl -sS -b "$COOKIE_JAR" "$BASE/api/v1/stats" | tee /tmp/kbpack-stats.json
echo

echo "== packages =="
curl -sS -b "$COOKIE_JAR" "$BASE/api/v1/packages?page=1&page_size=5" | tee /tmp/kbpack-packages.json
echo

echo "OK"
