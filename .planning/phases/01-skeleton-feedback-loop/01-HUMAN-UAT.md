---
status: partial
phase: 01-skeleton-feedback-loop
source: [01-VERIFICATION.md]
started: 2026-09-04T03:50:00Z
updated: 2026-09-04T03:50:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. CurseForge "test" instance in-app launch (BUILD-04 / Success Criterion 1)
expected: Launch the `test` instance from the CurseForge desktop app; wait for the main menu; open Mods. "Second Shift" is listed alongside owo-lib, accessories, and wildcard; the game reaches the main menu with no crash / no error screen; `C:/Users/user/curseforge/minecraft/Instances/test/logs/latest.log` contains `[SecondShift] common setup` and no `Unbound registry entries`.
result: [pending]

### 2. deployToTest file-lock branch (BUILD-05 / D-04)
expected: With Minecraft still running (instance open, holding a Windows lock on `mods/*.jar`), run `./gradlew deployToTest` once. The task fails with a `GradleException` containing "close Minecraft (it locks mods/*.jar) and re-run"; `build/libs/secondshift-0.1.0.jar` is unchanged (md5 still `f969d8c9c8592f8ee2d68e801b4198a8`).
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
