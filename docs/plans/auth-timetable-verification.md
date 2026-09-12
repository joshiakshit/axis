# Authentication and timetable verification plan

## Prerequisites

1. Set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`. Its compiler reports 17.0.20.1.
2. Install Android SDK Platform 35 and matching build tools. Platform tools alone are not a full SDK.
3. Configure the full SDK through `local.properties` or `ANDROID_HOME`.
4. Add `C:\platform-tools-latest-windows\platform-tools` to `PATH`, or use its absolute ADB path.
   If ADB tries to create `\.android`, set `ANDROID_USER_HOME` to a writable directory first.
5. Add `C:\Program Files\nodejs` to `PATH`, or use `npm.cmd` by absolute path.
6. JDK 21 is also available at `C:\Program Files\Java\jdk-21.0.12.1`, but use JDK 17 for this project.
7. Reconnect the HTTP Toolkit MCP server, or keep HTTP Toolkit open for manual inspection.
8. Keep credentials, OTPs, tokens, and student data out of logs and documents.

## Source review

Before running tests:

```powershell
git status --short
git diff --check
rg -n '3\.0\.3|3\.0\.8' app core backend --glob '!backend/package-lock.json'
```

Review the remaining 3.0.3 fixture in `backend/test/index.test.ts`. Change it only if the test represents the default configuration rather than an intentional override case.

## Android checks

Run in this order. Stop on the first failure and fix it.

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat test ktlintCheck detekt --console=plain
```

Check that tests cover:

- 3.0.9 fallback and remote override.
- Exact profile request fields.
- Account-scoped 24-hour profile cache.
- Forced profile refresh and concurrent request coalescing.
- No academic-year guessing.
- Strict timetable route selection.
- Schedule response validation.
- Exact client ID and profile academic year.
- v3 cache-key isolation.

## Backend checks

```powershell
Set-Location backend
npm test
npm run typecheck
```

Do not deploy until these checks pass.

## Remote configuration

Read the current tenant configuration without printing secrets. Set the deployed `appVersion` value to 3.0.9 through the existing admin path. Confirm the read-back value.

This is required because the deployed value can override the corrected app fallback.

## Device build and install

1. Confirm the phone is connected with `adb devices`.
2. Build the debug APK.
3. Install it with the existing project workflow.
4. Start a fresh HTTP Toolkit capture.
5. Redact sensitive values from saved evidence.

## Traffic acceptance checks

Confirm these facts in order:

1. OTP login sends `appversion: 3.0.9`.
2. OTP validation sends `appversion: 3.0.9`.
3. Axis calls `users/personaldetails` with JWT admission number and user type.
4. Axis sends no `InsertDeviceID` request.
5. The feature request uses the exact JWT client ID and profile academic year.
6. A false feature response leads to the legacy schedule endpoint.
7. A true and `"1"` feature response leads only to V1.
8. The schedule request keeps client ID case.
9. The displayed week matches the captured `emp_timetable` response.
10. A manual timetable refresh performs a fresh profile request.

## Completion record

Record command results and device findings in `HANDOFF.md`. Update `docs/PROJECT_MEMORY.md` only with stable facts and final decisions. Do not store raw credentials or student data.

## Device traffic record

Checked through HTTP Toolkit MCP on 2026-09-12. No credentials, tokens, or student values were saved.

- [x] OTP login sent `appversion: 3.0.9`.
- [x] OTP validation sent `appversion: 3.0.9`.
- [x] Personal details used the JWT admission number and user type with authorization.
- [x] No `InsertDeviceID` request was sent.
- [x] The feature request kept the exact JWT client ID and used the profile academic year.
- [x] The tenant returned a false feature result and Axis used the legacy schedule endpoint.
- [x] Unit tests covered the true and `"1"` selector. A temporary debug-only route override then sent two live schedule requests to V1. Both returned HTTP 200, and no new legacy schedule request followed.
- [x] The legacy schedule request kept the exact JWT client ID case and profile academic year.
- [x] The displayed week matched the captured response. The selected Saturday had zero classes in both.
- [x] A manual pull-to-refresh performed a fresh personal-details request before the feature and schedule calls.

The tenant continued to return the false feature result, so the selector response could not be mocked with the free HTTP Toolkit plan. The temporary override changed only route selection in the debug APK. It did not change the committed source. The normal APK was reinstalled after the check.
