---
status: resolved
phase: 01-skeleton-feedback-loop
source: [01-VERIFICATION.md]
started: 2026-09-04T03:50:00Z
updated: 2026-09-04T04:00:00Z
---

## Current Test

[complete — all tests passed]

## Tests

### 1. CurseForge "test" instance in-app launch (BUILD-04 / Success Criterion 1)
expected: Launch the `test` instance from the CurseForge app; "Second Shift" listed alongside owo-lib, accessories, wildcard; main menu, no crash; `logs/latest.log` has `[SecondShift] common setup` and no `Unbound registry entries`.
result: PASS — `C:/Users/user/curseforge/minecraft/Instances/test/logs/latest.log` (launch 04:52) shows Mod List `Accessories … / oωo … / Second Shift 0.1.0 (secondshift) / Wildcard 0.32.1`, `ReloadableResourceManager: … mod/secondshift …`, `[SecondShift] common setup - 1 item(s) registered: [secondshift:debug_marker]`, and no `Unbound registry entries`. Game running with no crash.

### 2. deployToTest file-lock branch (BUILD-05 / D-04)
expected: With `mods/secondshift-0.1.0.jar` locked, `./gradlew deployToTest` fails with a "close Minecraft (it locks mods/*.jar) and re-run" message; `build/libs/secondshift-0.1.0.jar` unchanged.
result: PASS — with an exclusive lock held on the destination jar, `./gradlew deployToTest` → `BUILD FAILED` (exit 1): `deployToTest: could not write to C:\Users\user\curseforge\minecraft\Instances\test\mods - close Minecraft (it locks mods/*.jar) and re-run. build/libs/secondshift-0.1.0.jar is unchanged.` — `build/libs/secondshift-0.1.0.jar` md5 unchanged (`f969d8c9…`), instance `mods/` intact (locked jar's delete failed, nothing removed). Note: Minecraft *at the main menu* on NeoForge 21.1 does NOT hold an exclusive lock — a plain `deployToTest` with the game open succeeds. The "close Minecraft" branch is correct defensive code and fires whenever a real lock exists.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None.
