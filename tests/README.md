# Local regression tests

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1` from the repository root. Only a JDK is required.

- `PolicyRegression.java`: 34 portable checks for firmware matching, protected packages and Android user/UID boundaries. Supplying `-FirmwarePolicy <path>` also checks the 61 package rules and 21 activity rules in an independently obtained, matching firmware XML (117 checks total).
- `host-src/`: 22 independent JVM scenarios compile and run the real core module against minimal, locally authored Android/Xposed API fixtures. Nine scenarios verify the exact nine-hook installation, environment guards, missing targets, rollback/retry, package protection, fold restoration and status-bar visibility. Six exercise `StatusBarInsetFix` geometry, deep copies, repeat calls, unsupported metadata, exception preservation and preflight. Five exercise `StatusBarAppearance` native flags, navigation-bit preservation, context changes, immersive requests, focused letterbox metadata and restoration. Two test the shared compact-height, font-scale, color and scene-marker policy.

The fold configuration pass, inset calculations and status-bar composition in these tests are explicitly simplified models. They cannot verify ART interception, actual fold event ordering, SystemUI colors, window sizes, animations, native inset dispatch, or camera/game rendering on a phone. `SystemUiEntry` is compiled against the Android SDK by `build.ps1`; its private SystemUI calls, layout and restoration require device verification rather than a simulated UI test.

Everything under `host-src` is test-only; `build.ps1` packages only application sources under `app/src/main/java`. No test framework classes are included in the APK.
