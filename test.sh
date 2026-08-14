#!/usr/bin/env bash
set -e

# One command, both suites. Docker is the toolchain, exactly like start.sh: no
# local JDK 21, Maven or Node needed to run this.

if [ ! -f backend/.env ]; then
  cp backend/.env.example backend/.env
  echo "Created backend/.env from backend/.env.example."
fi

COMPOSE="docker compose --env-file backend/.env"
NETWORK="$(basename "$PWD")_default"

# Git Bash rewrites container paths like /app into Windows paths before docker
# sees them; these two make the script behave the same there as on Linux/macOS.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"
HOST_DIR="$PWD"
if command -v cygpath >/dev/null 2>&1; then
  HOST_DIR="$(cygpath -w "$PWD")"
fi

# The backend suite talks to a real Postgres on purpose: a lock proved against an
# in-memory fake proves nothing (backend-rules SKILL.md §3).
echo "==> Starting Postgres"
$COMPOSE up -d postgres
$COMPOSE exec -T postgres sh -c 'until pg_isready -U "$POSTGRES_USER" >/dev/null 2>&1; do sleep 1; done'

echo "==> Backend tests"
docker run --rm \
  --network "$NETWORK" \
  --env-file backend/.env \
  -e DB_HOST=postgres \
  -e STORAGE_ROOT=/tmp/data \
  -v "$HOST_DIR/backend:/app" \
  -v book-illustrator-m2:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-21 mvn -B test

echo "==> Frontend tests"
docker run --rm \
  -v "$HOST_DIR/frontend:/app" \
  -w /app \
  node:22-alpine sh -c 'npm ci --no-audit --no-fund && npm run test'

echo "==> Both suites passed"
