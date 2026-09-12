# Axis 3.0.9 session handoff

## Current task

Repair Axis authentication metadata and student timetable routing for iCloudEMS 3.0.9.

The work is limited to these items:

- Send app version 3.0.9 during OTP login and validation.
- Load the academic year from `users/personaldetails`.
- Use the V1 timetable endpoint as a feature check.
- Fetch schedule data from the selected endpoint.
- Isolate timetable cache entries by student request context and route.

Do not add the official device-binding, biometric, geofence, selfie, or QR flow in this task. Do not redesign attendance or timetable cache duration.

## Product direction

Axis exists to make student data easier to access. It does not need to copy every official-app workflow. Add only server fields and calls that are required for correct Axis behavior.

## Confirmed server contract

OTP login and validation send `appversion: 3.0.9`. The existing remote configuration can override this fallback.

Personal details use:

```text
POST https://api.icloudems.com/users/personaldetails
Authorization: <current access token>

{
  "code": "<JWT admno>",
  "type": "<JWT user_type>"
}
```

Read `data.result.academic_year`. Cache it per account for 24 hours.

Timetable route selection uses:

```text
corecampus/student/schedulerandV1/controller/ctrl_tt_report_data_v1.php
```

Select V1 only when `status` is JSON boolean `true` and `enble_batch_wise_course_flow` is string `"1"`. Use legacy for every other response and for feature-check failures.

The schedule request uses the existing `getData` payload. It must use the exact JWT `client_id` and the profile academic year. Do not uppercase the client ID. Do not fall back to legacy after confirmed V1 selection.

## Completed implementation

The repair is committed in `efa74c3` and `440b10b`.

| Area | Files | State |
| --- | --- | --- |
| App version fallback | `RemoteConfig.kt` | Changed to 3.0.9 |
| Backend fallback | `backend/src/config.ts`, `backend/wrangler.toml`, backend docs and config tests | Changed to 3.0.9 |
| Profile API | `UserApi.kt`, `AppModule.kt`, `Auth.kt` | Added |
| Profile storage | `TokenManager.kt` | Added account-scoped academic year and fetch time |
| Student context | `AuthRepository.kt`, `Auth.kt` | Added `StudentRequestContext` and profile caching |
| Timetable routing | `TimetableRepository.kt` | Added strict feature selection, response validation, and v3 keys |
| Consumers | timetable, dashboard, attendance, planner, and export view models or services | Switched to `StudentRequestContext` |
| Tests | `AuthRepositoryTest.kt`, `TimetableRepositoryTest.kt`, `TimetableRoutingTest.kt`, `RemoteConfigRepositoryTest.kt` | Added or updated, passing |

See [implementation plan](docs/plans/auth-timetable-repair.md) for the complete checklist.

## Current verification state

Local validation passed on 2026-09-11 with JDK 17 and Android SDK 35:

- `:app:testDebugUnitTest` passed.
- `:app:assembleDebug` passed.
- `test ktlintCheck detekt` passed after targeted formatting and lint fixes.
- Backend Vitest passed: 4 files and 38 tests.
- Backend TypeScript type-check passed.
- `git diff --check` reports no errors. Git only reports existing LF-to-CRLF warnings.
- The deployed tenant config was read as `appVersion: 3.0.8`, updated through `PUT /v1/config`, and confirmed as `3.0.9`.
- The phone has the debug interception build with normal route selection restored. The update gate is cleared and no network-error screen is shown.
- The phone is connected through ADB and the user completed a fresh logout and login.
- HTTP Toolkit MCP traffic checks passed for OTP version 3.0.9, JWT profile identity, authorization, no `InsertDeviceID`, exact client ID case, profile academic year, false-feature fallback, legacy schedule routing, and schedule payload context.
- The displayed week matched the captured `emp_timetable` response. The selected Saturday had zero classes in both.
- A user pull-to-refresh created a fresh personal-details request, followed by the feature check and legacy schedule request. All returned HTTP 200.
- The tenant continued to return the false feature result. Unit tests cover the strict boolean `true` and string `"1"` selector.
- A temporary debug-only route override sent two live schedule requests to V1. Both returned HTTP 200, and no new legacy schedule request followed.
- The normal route-selection source and normal APK were restored after the V1 check. Temporary V1 APK files were removed.
- The standard generated `app-debug.apk` had been overwritten by the forced-route build. It and its generated metadata were removed. Rebuild the normal debug APK before a future install.
- The temporary debug certificate override was removed after traffic validation. Release certificate pinning is unchanged.
- Codex's normal MCP launcher was blocked from the HTTP Toolkit control pipe with `EPERM`. Running the bundled MCP server outside the sandbox exposed the read-only traffic tools without restarting or clearing the capture.

The installed tool state is:

- JDK 17 is installed at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`.
- JDK 21 is installed at `C:\Program Files\Java\jdk-21.0.12.1`.
- `javac` was verified for both JDKs.
- Android platform tools are installed at `C:\platform-tools-latest-windows\platform-tools`.
- In the restricted task shell, ADB tried to create `\.android` and received access denied. Set `ANDROID_USER_HOME` to a writable directory before using ADB if this repeats.
- Node.js 24.21.0 and npm 11.19.0 are installed at `C:\Program Files\nodejs`.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` are empty.
- `local.properties` was restored from the keystore backup. Its SDK and release-keystore paths were changed from old Linux paths to valid Windows paths. All other values were preserved. The file is ignored by Git.
- Android Studio created `C:\Users\Evo\AppData\Local\Android\Sdk`. Platform 35 and its `android.jar` are installed. Platform `android-37.0` and build tools 36.0.0 are also present. Android SDK command-line tools are not installed, but they are not required for the current Gradle build.
- Build Tools 34 and backend dependencies were installed during validation.
- The project-local Gradle cache at `C:\Coding\Axis\.gradle-user` now exists again.

The research root now contains only `v3.0.8/` and `v3.0.9/`. Historical inputs and evidence were retained. Reproducible tool caches and APK build intermediates were removed. See `C:\Coding\Axis\research\research\v3.0.9\WORKLOG.md`.

## Known open items

1. Build and publish the signed 1.1.1 release.

Preserve the user's untracked `AGENTS.md`. Do not overwrite unrelated working-tree changes.

## Next concrete step

Run the release validation gate with a fresh Gradle daemon. Then publish the signed 1.1.1 build without raising the forced-update floor.

Do not save credentials, OTPs, tokens, or student data in the evidence.
