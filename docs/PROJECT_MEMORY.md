# Axis API notes

These notes record the authentication and timetable repair from 2026-09-12.
Use the current source and tests to verify implementation details.

## API findings from September 2026

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

## Authentication and timetable repair decisions

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

## Checks still recorded as open

- The integration reports did not verify device layout, accessibility, PNG sharing, QR camera behavior, or performance on a device.
- A real classroom QR submission still needs confirmation in the historical handoff.
- The integration reports flag backend identity-token signature verification for a separate security review.
- The old handoff did not confirm deployment of the backend migration. Check deployment state before applying migrations.
- The old handoff left update installation after returning from Android permission settings unverified.

These are historical gaps, not a fresh assessment of release 1.2.2.

## Historical records

The old handoff, repair plans, and agent reports are available in Git at commit `0268ac1`.
Use `git show 0268ac1:HANDOFF.md` or `git ls-tree -r --name-only 0268ac1 docs/plans` to inspect them.
