# Axis project memory

Last updated: 2026-09-12

## Product intent

Axis is an easier interface for student data. It is not an exact copy of iCloudEMS. Match the official client only where the server contract requires it.

## Current priority

Repair authentication metadata and timetable correctness for iCloudEMS 3.0.9. QR and broad freshness work are deferred.

## Stable research findings

- The official app version is 3.0.9 with version code 242.
- OTP login and validation send `appversion: 3.0.9`.
- Existing access-token and refresh-token field names remain valid.
- No authorization-token rotation or response-schema change was confirmed.
- Timetable access does not require adding `InsertDeviceID` to Axis.
- `users/personaldetails` accepts JWT admission number as `code` and JWT `user_type` as `type`.
- `data.result.academic_year` is the timetable academic year.
- The V1 timetable endpoint first acts as a feature check.
- Only JSON boolean `status: true` plus string `enble_batch_wise_course_flow: "1"` selects V1.
- All other feature results select legacy.
- A feature-check network failure should also select legacy.
- The client ID must keep the exact JWT case.
- A valid schedule has a recognized container such as `emp_timetable`.
- Empty day arrays are valid schedule data.
- The small feature response is not schedule data.

## Current implementation decisions

- Keep the current Axis device UUID behavior.
- Keep the existing remote authorization-token mechanism.
- Cache profile academic year per account for 24 hours.
- Coalesce concurrent profile requests.
- Allow normal loads to use an existing cached year after a profile network failure.
- Force a profile refresh during manual timetable refresh.
- Fail with a retryable error when no academic year exists.
- Do not infer timetable year from the selected attendance semester.
- Use `StudentRequestContext` for all timetable consumers.
- Use v3 timetable keys with student, branch, client, year, route, and date range.
- Do not retry legacy after a confirmed V1 selection.

## Deferred decisions

- QR scanner repair and QR template flow.
- Device registration and binding.
- Biometric, geofence, live location, and selfie rules.
- Attendance cache policy.
- Timetable cache duration.
- Manual-refresh error presentation.

## Work state

The source implementation and unit tests pass local Android and backend validation. The repair is committed in `efa74c3` and `440b10b`.

Installed and verified tools:

- JDK 17 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`;
- JDK 21 at `C:\Program Files\Java\jdk-21.0.12.1`;
- Android platform tools at `C:\platform-tools-latest-windows\platform-tools`;
- Node.js and npm at `C:\Program Files\nodejs`.

The deployed tenant config was updated and confirmed with `appVersion: 3.0.9` on 2026-09-11.

Live traffic validation on 2026-09-12 confirmed the OTP version, JWT profile identity, authorization, absence of device registration, exact client ID case, profile academic year, false-feature fallback, legacy schedule payload, displayed-week data, and forced profile refresh.

The tenant did not expose the true selector response. Unit tests cover the strict boolean `true` and string `"1"` selector. A temporary debug-only route override exercised the live V1 schedule endpoint. Two V1 schedule requests returned HTTP 200, and no new legacy request followed. The normal source and APK were restored after the check.

`local.properties` was restored from `C:\Coding\Axis\keystore\keystore\local.properties.backup`. The old Linux SDK and keystore paths were replaced with current Windows paths. All secret values were preserved. Never commit or print this file.

Android Studio created `C:\Users\Evo\AppData\Local\Android\Sdk`. Platform 35 and its `android.jar` are installed. Platform `android-37.0` and build tools 36.0.0 are also present. Android SDK command-line tools are not installed, but they are not required for the current Gradle build.

The project-local Gradle cache at `C:\Coding\Axis\.gradle-user` was recreated for validation.

The research root is organized into only `v3.0.8/` and `v3.0.9/`. Historical inputs and evidence remain. Reproducible tool caches and build intermediates were removed. Apktool and uber-apk-signer remain under `v3.0.9/tools/`.

## References

- Session handoff: `HANDOFF.md`
- Implementation plan: `docs/plans/auth-timetable-repair.md`
- Verification plan: `docs/plans/auth-timetable-verification.md`
- Research summary: `C:\Coding\Axis\research\research\v3.0.9\INITIAL_INTELLIGENCE.md`
- Hermes review: `C:\Coding\Axis\research\research\v3.0.9\HERMES_REVIEW.md`
- Research log: `C:\Coding\Axis\research\research\v3.0.9\WORKLOG.md`
