#!/usr/bin/env bash
# One-command launcher: starts RiskGuard and opens the demo page.
# Needs Java 21+ only (Maven is bundled via ./mvnw).
set -e
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install it with: brew install openjdk@21" >&2
  exit 1
fi

URL="http://localhost:8080"

if [ -z "$NO_OPEN" ]; then
  (
    for _ in $(seq 1 120); do
      curl -s -o /dev/null "$URL" && break
      sleep 1
    done
    if command -v open >/dev/null 2>&1; then open "$URL"
    elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL"; fi
  ) &
fi

echo "Starting RiskGuard on $URL (Ctrl+C to stop)..."
exec ./mvnw -q spring-boot:run
