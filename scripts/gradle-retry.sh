#!/bin/bash
# Runs ./gradlew with the given arguments, retrying while failures look like Maven Central rate limits
# ("429 Too Many Requests", which Gradle sometimes reports as "Could not find x.jar"). Common in Claude
# Code cloud sessions. Gradle keeps what it downloaded, so each retry gets further.
#   scripts/gradle-retry.sh test
cd "$(dirname "$0")/.." || exit 1
LOG=$(mktemp)
for i in $(seq 1 15); do
  ./gradlew "$@" --no-daemon -q --max-workers=2 > "$LOG" 2>&1 && { echo "OK (try $i)"; rm -f "$LOG"; exit 0; }
  if ! grep -qE "status code 429|Could not find .*\.(jar|pom)|Could not find org\.|Could not find com\." "$LOG"; then
    grep -v JAVA_TOOL_OPTIONS "$LOG" | grep -E '^e: |error:|FAILED|What went wrong|^> |Exception|expected|Test.*FAILED' | head -40
    echo "Full log: $LOG"
    exit 1
  fi
  echo "Rate-limited by Maven, retry $i"; sleep $((i * 4))
done
echo "Still rate-limited after 15 tries. Full log: $LOG"; exit 1
