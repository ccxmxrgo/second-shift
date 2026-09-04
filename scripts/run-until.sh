#!/usr/bin/env bash
#
# run-until.sh — launch a Gradle run task, wait for a log marker (or a timeout),
#                then kill the whole JVM tree and return.
#
# Built for Git Bash / MSYS2 on Windows (no pkill/pgrep; taskkill for the kill).
#
# Positional args:
#   $1  gradle run task            e.g. runClient | runServer | runData
#   $2  extended-regex log marker  e.g. 'Sound engine started|OpenAL initialized'
#   $3  timeout seconds            default 300
#   $4  explicit log-file path     default '' -> auto-discover run/logs + runs/*/logs
#   $5  mode                       'ready' (default) or 'abort'
#
# Modes:
#   ready  — the marker means "the game is up". Gradle exiting early is a FAILURE
#            (exit = gradle's own exit code).
#   abort  — we EXPECT the launch to die (deliberate self-check abort test). A
#            marker match OR a non-zero gradle exit is SUCCESS (exit 0); a clean
#            gradle exit with no marker is a failure (exit 1).
#
set -u

TASK="${1:?usage: run-until.sh <task> <marker-regex> [timeout] [logfile] [ready|abort]}"
MARKER="${2:?missing marker regex}"
TIMEOUT="${3:-300}"
LOGFILE="${4:-}"
MODE="${5:-ready}"

POLL_INTERVAL=2

log() { printf '[run-until] %s\n' "$*" >&2; }

discover_log() {
  # newest of the known latest.log locations MDG 2.0.146 might use
  ls -t run/logs/latest.log runs/*/logs/latest.log 2>/dev/null | head -1
}

kill_tree() {
  log "killing JVM tree (gradle pid ${GRADLE_PID:-?})"
  ./gradlew --stop >/dev/null 2>&1 || true
  if command -v taskkill >/dev/null 2>&1 && [ -n "${GRADLE_PID:-}" ]; then
    # //F force, //T tree — doubled slashes defeat MSYS path mangling
    taskkill //F //T //PID "$GRADLE_PID" >/dev/null 2>&1 || true
  else
    kill $(jobs -p) 2>/dev/null || true
    [ -n "${GRADLE_PID:-}" ] && kill "$GRADLE_PID" 2>/dev/null || true
  fi
  # give the OS a moment to reap
  sleep 2
}

# Resolve / prime the log file.
if [ -z "$LOGFILE" ]; then
  LOGFILE="$(discover_log)"
fi
if [ -n "$LOGFILE" ] && [ -f "$LOGFILE" ]; then
  log "truncating existing log: $LOGFILE"
  : > "$LOGFILE"
fi

log "launching: ./gradlew $TASK  (timeout ${TIMEOUT}s, mode ${MODE})"
./gradlew "$TASK" --console=plain &
GRADLE_PID=$!

DEADLINE=$(( $(date +%s) + TIMEOUT ))
GRADLE_RC=""

while :; do
  # (re)discover the log file if we didn't have one at launch
  if [ -z "$LOGFILE" ] || [ ! -f "$LOGFILE" ]; then
    CANDIDATE="$(discover_log)"
    [ -n "$CANDIDATE" ] && LOGFILE="$CANDIDATE" && log "resolved log: $LOGFILE"
  fi

  # marker match?
  if [ -n "$LOGFILE" ] && [ -f "$LOGFILE" ] && grep -Eq "$MARKER" "$LOGFILE"; then
    log "marker matched: /$MARKER/"
    kill_tree
    exit 0
  fi

  # gradle process still alive?
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    wait "$GRADLE_PID" 2>/dev/null
    GRADLE_RC=$?
    log "gradle exited early rc=$GRADLE_RC"
    if [ "$MODE" = "abort" ]; then
      if { [ -n "$LOGFILE" ] && [ -f "$LOGFILE" ] && grep -Eq "$MARKER" "$LOGFILE"; } || [ "$GRADLE_RC" -ne 0 ]; then
        log "abort-mode success (marker or non-zero rc)"
        kill_tree
        exit 0
      fi
      log "abort-mode failure: clean exit, no marker"
      kill_tree
      exit 1
    fi
    # ready mode: early exit is a failure — propagate gradle's rc
    kill_tree
    exit "$GRADLE_RC"
  fi

  # timeout?
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    log "TIMEOUT after ${TIMEOUT}s — last 60 log lines:"
    [ -n "$LOGFILE" ] && [ -f "$LOGFILE" ] && tail -n 60 "$LOGFILE" >&2
    kill_tree
    exit 1
  fi

  sleep "$POLL_INTERVAL"
done
