# Phase 1: User Setup Required

**Generated:** 2026-09-04
**Phase:** 01-skeleton-feedback-loop
**Status:** Complete

Claude automated everything possible; this item needs the CurseForge desktop app, which Claude cannot drive.

## Environment Variables

None.

## Account Setup

None — no new account required.

## Dashboard Configuration

- [x] **CurseForge "test" instance exists and is closed for `deployToTest`**
  - Location: CurseForge app → Minecraft → Instances → `test`
  - Path on disk: `C:\Users\user\curseforge\minecraft\Instances\test` (`neoforge-21.1.248` / MC `1.21.1`)
  - Verified present at execution time with `owo-lib`, `accessories`, `wildcard` in `mods/`.
  - `./gradlew deployToTest` refuses to run (actionable error) if Minecraft is open and holding a lock on `mods/*.jar` — close the instance before deploying.

## Verification

```bash
# instance mods/ holds exactly one secondshift jar and no legacy dashed jar
ls "C:/Users/user/curseforge/minecraft/Instances/test/mods/" | grep -E 'secondshift-.*\.jar'   # -> secondshift-0.1.0.jar
ls "C:/Users/user/curseforge/minecraft/Instances/test/mods/" | grep 'second-shift-0.1.0.jar'   # -> (nothing)
```

Expected: `secondshift-0.1.0.jar` present (md5-identical to `build/libs/secondshift-0.1.0.jar`), no `second-shift-0.1.0.jar`.

## Remaining human verification (end-of-phase UAT)

Deferred to phase verification (`human_verify_mode: end-of-phase`) — see `01-HUMAN-UAT.md` if generated:

1. Launch the CurseForge `test` instance from the CurseForge app; confirm it reaches the main menu with **Second Shift** listed in Mods alongside owo-lib / accessories / wildcard, no crash, and `…/Instances/test/logs/latest.log` contains `[SecondShift] common setup` and no `Unbound registry entries`.
2. With Minecraft still running, run `./gradlew deployToTest` once and confirm it fails with the "close Minecraft" message and `build/libs/secondshift-0.1.0.jar` is unchanged (the D-04 file-lock branch — the only path with no automated coverage).

---

**Status: Complete** — the instance exists and `deployToTest` succeeded against it. The two items above are in-game checks tracked for end-of-phase UAT, not blockers.
