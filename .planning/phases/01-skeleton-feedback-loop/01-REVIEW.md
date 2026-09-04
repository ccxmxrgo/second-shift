---
phase: 01-skeleton-feedback-loop
reviewed: 2026-09-04T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - build.gradle
  - settings.gradle
  - gradle.properties
  - gradle/wrapper/gradle-wrapper.properties
  - .gitattributes
  - .gitignore
  - src/main/templates/META-INF/neoforge.mods.toml
  - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java
  - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
  - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
  - src/main/resources/pack.mcmeta
  - src/main/resources/assets/secondshift/lang/en_us.json
  - scripts/run-until.sh
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-09-04
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Phase 1 is a from-scratch NeoForge 1.21.1 skeleton: Gradle/MDG wiring, a minimal
`@Mod` entrypoint, one throwaway `DeferredItem` (`debug_marker`), an
`FMLLoadCompleteEvent` unbound-registry self-check, a `Dist.CLIENT` isolation class,
a `deployToTest` Gradle task, and a Git-Bash launch harness.

The Java is clean for a skeleton. `SecondShift`, `ModItems`, `ModRegistrySelfCheck`,
and `ClientModBusEvents` were traced for null/type/edge issues, bus wiring, thread
safety in `enqueueWork`, and client-class leakage; nothing incorrect was found, and
the summaries record that the deliberate "detach a register" abort was actually
exercised on both `runClient` and `runServer`. The `build.gradle` `deployToTest`
task is config-cache-safe as claimed (providers captured at configuration time, no
`project` reference in `doLast`), and its glob-delete filter (`\Qsecondshift\E-.*\.jar`)
is correctly anchored via Groovy `==~`.

No BLOCKER-class defects. The findings below are robustness and maintainability
issues, concentrated in `scripts/run-until.sh` (a Windows launch harness whose
failure modes are silent timeouts and false pass/fail) and a maintainability hazard
in the two-list self-check design.

Items explicitly out of scope because the phase context declares them intentional:
`debug_marker` has no model/texture/lang; `en_us.json` is `{}`; `.gitkeep` files land
in the jar.

## Warnings

### WR-01: `run-until.sh` `./gradlew --stop` kills every Gradle daemon on the machine

**File:** `scripts/run-until.sh:41` (`kill_tree`, called from lines 79, 92-99, 108)
**Issue:** `kill_tree` unconditionally runs `./gradlew --stop`, which stops **all**
Gradle daemons for this Gradle version, not just the one launched by this script.
On a normal dev machine the project is usually also open in an IDE with its own
Gradle daemon; every `run-until.sh` invocation (including the marker-match success
path at line 79) will tear that daemon down mid-work, forcing a cold restart and
losing any in-progress IDE sync/build. The harness is advertised as "kill the whole
JVM tree and return" for a single launch, not "stop all Gradle activity."
**Fix:** Prefer severing just this run: kill the launched client tree first
(`taskkill //F //T //PID "$GRADLE_PID"`) and let Gradle's client-disconnect
cancellation tear down the forked game process. Only fall back to `./gradlew --stop`
if orphans are actually detected, or scope the kill to the specific daemon PID:
```sh
kill_tree() {
  if command -v taskkill >/dev/null 2>&1 && [ -n "${GRADLE_PID:-}" ]; then
    taskkill //F //T //PID "$GRADLE_PID" >/dev/null 2>&1 || true
  else
    kill $(jobs -p) 2>/dev/null || true
  fi
  sleep 2
  # only if the JVM tree is confirmed still alive:
  # ./gradlew --stop >/dev/null 2>&1 || true
}
```

### WR-02: `run-until.sh` log-file discovery locks onto a stale path and never recovers

**File:** `scripts/run-until.sh:34-37` (`discover_log`), `54-60`, `71-74`
**Issue:** `discover_log` returns the newest of
`run/logs/latest.log runs/*/logs/latest.log`. Whichever it picks at startup is
truncated (`: > "$LOGFILE"`, line 59) and then watched. The in-loop re-discovery at
lines 71-74 only fires while `LOGFILE` is empty **or the file does not exist** — once
a path is chosen and the file exists, it is never re-evaluated. If a stale
`runs/*/logs/latest.log` (or a `run/logs/latest.log` left by a different task) has a
newer mtime than the file the current task will actually write, the script truncates
and greps the wrong file forever: it burns the full timeout (up to 300s) and, in
`ready` mode, returns a spurious non-zero failure even though the game launched
correctly. The `01-01-SUMMARY` notes the `runs/*/logs` glob "never matched" today,
so this is latent, but the harness is the project's core feedback loop and a silent
5-minute false failure is expensive.
**Fix:** Derive the expected log path from `$TASK` / MDG's known layout
(`run/logs/latest.log` for all run tasks per `01-01-SUMMARY`) instead of an
mtime race, or re-run discovery every poll and switch if a newer candidate appears
after launch. At minimum, drop the `runs/*/logs` fallback now that it is confirmed
unused.

### WR-03: Self-check registry list must be maintained in two places, and omission fails silently

**File:** `src/main/java/com/cxmxrgo/secondshift/SecondShift.java:34` and
`src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java:43-46`
**Issue:** Every new registry class must be added to *both* the `SecondShift`
constructor (`X.REG.register(modBus)`) and the `ModRegistrySelfCheck` `Stream.of(...)`
list (D-10). The two failure modes are asymmetric and both bad: forgetting the
constructor line is what the self-check is designed to catch, but **forgetting the
`Stream.of` line silently removes that register from the guardrail** — the unbound-
holder crash the whole file exists to prevent comes back with no warning. Comments
in both files acknowledge the coupling, but a comment is not a mechanism, and Phase 2
already plans to add `ModItems`/`ModBlocks`/`ModBlockEntities`.
**Fix:** Keep one canonical collection and iterate it from both sites:
```java
// e.g. in a ModRegistries class
public static final List<DeferredRegister<?>> ALL = List.of(ModItems.ITEMS /*, ModBlocks.BLOCKS, ... */);
```
`SecondShift` does `ModRegistries.ALL.forEach(r -> r.register(modBus));` and
`ModRegistrySelfCheck` streams `ModRegistries.ALL`. One list, one place to forget.

## Info

### IN-01: `neoforge.mods.toml` loaderVersion `[1,)` deviates from the MDK's `[4,)` with no rationale

**File:** `gradle.properties:19`, consumed by
`src/main/templates/META-INF/neoforge.mods.toml:2`
**Issue:** FML for 1.21.1 is major version 4; the official MDK ships
`loader_version_range=[4,)`. `[1,)` is always satisfied and imposes no meaningful
lower bound, so it provides no protection against a future incompatible FML major.
Unlike `neo_version_range` (which carries a multi-line justification comment right
above it), this deviation is undocumented.
**Fix:** Use `[4,)` to match the MDK, or add a one-line comment explaining the
intentional loosening.

### IN-02: `pack.mcmeta` `pack_format: 48` is the datapack number, not the resource-pack number

**File:** `src/main/resources/pack.mcmeta:4`
**Issue:** For Minecraft 1.21.1 the resource-pack format is `34`; `48` is the
*data pack* format. This is a deliberate project choice (POL-09 / REQUIREMENTS.md
POL-09, checked off) and is effectively inert for a NeoForge mod because
mod-provided packs bypass pack-version gating and always load. Flagged only so a
future reviewer chasing client asset-pack warnings knows the value does not
describe the assets side.
**Fix:** None required while POL-09 stands. If asset-pack compatibility warnings
ever appear in `latest.log`, revisit with `34` (or a `supported_formats` range).

### IN-03: `deployToTest` deletes stale jars before copying the new one

**File:** `build.gradle:131-142`
**Issue:** Stale `secondshift-*.jar` files are deleted (lines 131-138) *before*
`Files.copy` runs (line 139). If the copy throws (e.g. an antivirus lock on the
destination that did not block the delete), the instance `mods/` folder is left with
**no** `secondshift` jar. The catch block message (lines 144-146) correctly says
`build/libs/<jar>` is unchanged but does not mention the instance was left without
the mod, so the user may launch a now-mod-less instance without realizing why.
**Fix:** Copy to a temp name and atomically move into place, or delete stale jars
only *after* the copy succeeds. Alternatively, extend the error message to state
that the instance `mods/` folder no longer contains a `secondshift` jar.

### IN-04: `run-until.sh` positional args `TIMEOUT` and `MODE` are unvalidated

**File:** `scripts/run-until.sh:26` (`TIMEOUT`), `28` (`MODE`), `66`, `88`
**Issue:** `TIMEOUT="${3:-300}"` is fed straight into `DEADLINE=$(( $(date +%s) + TIMEOUT ))`
(line 66). A non-numeric `$3` makes the arithmetic evaluate a bareword as a variable
name and, under `set -u`, aborts with a cryptic `unbound variable` rather than a
usage error. `MODE="${5:-ready}"` is only ever compared against `"abort"` (line 88),
so any typo (`redy`, `Abort`, `read`) silently gets `ready`-mode semantics — the
opposite pass/fail contract from what the caller may have intended.
**Fix:** Validate early:
```sh
case "$TIMEOUT" in ''|*[!0-9]*) echo "timeout must be an integer" >&2; exit 2;; esac
case "$MODE" in ready|abort) ;; *) echo "mode must be ready|abort" >&2; exit 2;; esac
```

### IN-05: Deliberate use of the deprecated-for-removal `EventBusSubscriber` `bus` attribute

**File:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java:26`,
`src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java:21`
**Issue:** Per `01-01-SUMMARY`, `bus = EventBusSubscriber.Bus.MOD` is
`@Deprecated(forRemoval = true)` in NeoForge 21.1.248 and emits a `[removal]` compile
warning on every build. Keeping it is an explicit "documentation parity" decision and
is safe while the project pins NeoForge for all of v1, but it means the build is
never warning-clean and the annotations will fail to compile on any NeoForge bump.
The accompanying comments correctly state that FML now derives the bus from the event
type, so the attribute is genuinely redundant.
**Fix:** Acceptable to keep for v1 as documented. When NeoForge is next bumped, drop
the `bus = ...` argument from both classes (the `IModBusEvent`-derived routing keeps
them on the mod bus).

### IN-06: `run-until.sh` `GRADLE_RC` can capture `wait` failure instead of the real exit code

**File:** `scripts/run-until.sh:85-100`
**Issue:** After `kill -0` reports the process dead (line 84), `wait "$GRADLE_PID"`
(line 85) may return `127` if the child was already reaped in the interval, and
`GRADLE_RC=$?` then records `127`. In `ready` mode this is propagated as the script's
exit code (line 100) instead of Gradle's real code; in `abort` mode a genuine clean
exit that races with reaping would be misread as `rc != 0` and reported as success
(lines 89-91). Narrow race, low probability on a single foreground child.
**Fix:** Capture the code from the same `wait` that detects exit, e.g. loop on
`if ! kill -0 "$GRADLE_PID" 2>/dev/null; then wait "$GRADLE_PID"; GRADLE_RC=$?; ...`
is already close — additionally guard against the empty/`127` case:
`[ -n "$GRADLE_RC" ] && [ "$GRADLE_RC" -lt 128 ] || GRADLE_RC=1` before branching.

---

_Reviewed: 2026-09-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
