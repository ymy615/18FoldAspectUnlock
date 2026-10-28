# Local regression tests

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1` from the repository root. Only a JDK is required.

- `PolicyRegression.java`: 34 portable checks for the exact `OS4.0.26.0.XPNCNXM` firmware, protected packages and Android user/UID boundaries. Supplying `-FirmwarePolicy <path>` also checks the current 19 package rules and 3 activity rules in an independently obtained, matching firmware XML (57 checks total). The older 21-version XML is deliberately not accepted as the current reference.
- `host-src/`: 29 independent JVM scenarios compile and run real application helper sources against minimal, locally authored Android/Xposed API fixtures. Nine scenarios verify the exact twelve-hook core installation, environment guards, missing targets, rollback/retry (including failure at the twelfth installation), package protection, fold restoration and status-bar visibility. Six exercise `StatusBarInsetFix` geometry, deep copies, repeat calls, unsupported metadata, exception preservation and preflight. Five exercise `StatusBarAppearance` native flags, navigation-bit preservation, context changes, immersive requests, focused letterbox metadata and restoration. Two test the shared compact-height, font-scale, color and scene-marker policy. Seven exercise `WidthModePolicy` and `WidthModeController`.

The width scenarios check the inspected 1168 x 1712 outer-display geometry: narrow retains native 20:9 and the 1080px cap; wide restores 16:9 and `1712/2076` scale. They distinguish a negative native scale caused by the cap from explicit scale-mode-3 opt-out, and cover invalid geometry, protected UIDs/packages, game/split/inner scenes, persisted defaults and invalid mode requests. Receiver tests verify the actual manifest's signature permission plus exported/permission/handler registration arguments, ordered result values, registration retry and persistence failure. Refresh tests verify all eligible caches are invalidated before process updates, processes/tasks are deduplicated, only visible attached activities receive native application calls, background activities are deferred, and native app ratios/runtime flags remain unchanged. Partial/fatal refresh failure is reported as saved/deferred; repeating the same mode retries refresh without another settings write.

For the current local firmware snapshot, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1 -FirmwarePolicy .\evidence\os4.0.26.0\builtin-wide_fold_size_compat.xml
```

The fold configuration pass, inset calculations, native scale oracle and status-bar composition in these tests are explicitly simplified models. Context/Settings/receiver fixtures are recorders, not an implementation of Android permission enforcement, Binder delivery, thread scheduling or process restart. They cannot verify ART interception, actual fold event ordering, SystemUI colors, window sizes, animations, native inset dispatch, activity relaunch behavior, or camera/game rendering on a phone. `SystemUiEntry` and the launcher are compiled against the Android SDK by `build.ps1`; their UI behavior requires device verification rather than a simulated UI framework.

Everything under `host-src` is test-only; `build.ps1` packages only application sources under `app/src/main/java`. No test framework classes are included in the APK.
