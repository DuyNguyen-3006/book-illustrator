#!/usr/bin/env bash
set -e

if [ ! -f backend/.env ]; then
  cp backend/.env.example backend/.env
  echo "Created backend/.env from backend/.env.example — set GEMINI_API_KEY before running pipeline steps."
fi

if [ ! -f frontend/.env ]; then
  cp frontend/.env.example frontend/.env
  echo "Created frontend/.env from frontend/.env.example."
fi

docker compose --env-file backend/.env up --build
