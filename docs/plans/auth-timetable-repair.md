# Authentication and timetable repair plan

Status: Complete. Local validation and live traffic checks passed.

## Scope

Implement only the minimum server requirements for authentication and timetable correctness.

Deferred work:

- QR scanner repair.
- Device registration and binding.
- Live location and selfie handling.
- Attendance cache changes.
- Timetable cache-duration changes.
- Manual-refresh error presentation.

## Authentication

- [x] Change the Axis fallback `appversion` from 3.0.8 to 3.0.9.
- [x] Change the Worker and Wrangler fallback from 3.0.3 to 3.0.9.
- [x] Update related backend documentation and config tests.
- [x] Update the deployed tenant remote configuration to 3.0.9.
- [x] Keep the remote authorization-token mechanism.
- [x] Keep the current Axis device ID behavior.
- [x] Do not add `InsertDeviceID` or device-binding UI.

Add the minimum profile request:

```text
POST users/personaldetails
Authorization: current access token

{
  "code": "<JWT admission number>",
  "type": "<JWT user_type>"
}
```

- [x] Add `userType` and `academicYear` to `UserInfo`.
- [x] Parse `data.result.academic_year`.
- [x] Ignore unrelated personal-details fields.
- [x] Store the academic year in encrypted account-scoped preferences.
- [x] Store a profile-fetch timestamp.
- [x] Reuse profile data for up to 24 hours.
- [x] Refresh when missing.
- [x] Force profile refresh during a manual timetable refresh.
- [x] Coalesce concurrent profile requests.
- [x] Use a cached academic year after a normal-load profile failure.
- [x] Return a retryable error when no academic year is available.
- [x] Do not infer timetable academic year from the selected attendance semester.

## Student request context

Use one context for all timetable calls:

```kotlin
data class StudentRequestContext(
    val admno: String,
    val brId: Int,
    val clientId: String,
    val academicYear: String,
)
```

- [x] Build it in `AuthRepository.requireStudentRequestContext(forceProfileRefresh)`.
- [x] Read admission number, branch, user type, and client ID from the JWT.
- [x] Read academic year from stored or refreshed personal details.
- [x] Update timetable repository methods to accept the context.

## Timetable feature check

Call:

```text
corecampus/student/schedulerandV1/controller/ctrl_tt_report_data_v1.php
```

with:

```json
{
  "from": "app",
  "empid": "",
  "method": "getbatchwisebranchwiseflow",
  "br_id": "<branch>",
  "admno": "<admission number>",
  "client": "<exact JWT client_id>",
  "acadyr": "<profile academic_year>"
}
```

- [x] Select V1 only for JSON boolean `true` and string `"1"`.
- [x] Select legacy for boolean false.
- [x] Select legacy for string false.
- [x] Select legacy for missing or malformed fields.
- [x] Select legacy when the feature check fails.

## Timetable schedule request

Send the existing request after route selection:

```json
{
  "from": "app",
  "empid": "",
  "action": "wdefault",
  "method": "getData",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "br_id": "<branch>",
  "admno": "<admission number>",
  "room": "",
  "client": "<exact JWT client_id>",
  "acadyr": "<profile academic_year>"
}
```

- [x] Send it to legacy when the feature flag is off.
- [x] Send it to V1 when the feature flag is on.
- [x] Preserve JWT client ID case.
- [x] Do not retry legacy after confirmed V1 selection.
- [x] Require a recognized schedule container.
- [x] Accept recognized empty day arrays.
- [x] Reject the feature response as schedule data.

## Cache isolation

- [x] Bump timetable keys from v2 to v3.
- [x] Include admission number.
- [x] Include branch.
- [x] Include client ID.
- [x] Include academic year.
- [x] Include route.
- [x] Include start and end dates.
- [x] Keep the current timetable cache duration.

## Consumers

- [x] Timetable screen.
- [x] Dashboard forecast.
- [x] Attendance forecast.
- [x] Planner.
- [x] Timetable export.

## Test coverage drafted

- [x] OTP login and validation use 3.0.9.
- [x] Remote config can override the fallback.
- [x] Personal details use JWT admission number and user type.
- [x] Academic year parsing and account-scoped storage.
- [x] Cached profile reuse within 24 hours.
- [x] Forced profile refresh.
- [x] Missing academic year error.
- [x] Strict V1 selection.
- [x] Legacy selection cases.
- [x] Feature response rejection.
- [x] Valid empty timetable acceptance.
- [x] Profile year and exact client ID in requests.
- [x] Cache-key isolation dimensions.
- [x] Compile and execute all drafted tests.

## Acceptance gate

The work is not complete until all checks in [auth-timetable-verification.md](auth-timetable-verification.md) pass and phone traffic matches the expected contract.

The gate passed on 2026-09-12. The tenant supplied the false-route case. A temporary debug-only route override exercised the live V1 schedule endpoint without a legacy retry. Unit tests cover the strict boolean `true` and string `"1"` selector. The normal app was restored after the check.
