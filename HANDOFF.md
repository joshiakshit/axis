# Axis handoff

## Current task

Finish Notifications, add university events and holidays, and clean up reliability and privacy. The implementation is complete and committed in focused changes. Final device screenshots are verified.

## Decisions

- Notifications use the student-authenticated UserApi. The login client overwrites the student token and is unsuitable for these requests.
- Read status is stored per college and account on this device. The inspected official notification feed has no read-update call. Refresh keeps read status.
- Failed or malformed notification/calendar responses remain errors. Cached lists, including confirmed empty lists, retain the last successful fetch time.
- Calendar requests send br_id, fromdate, todate, and lastmodifiedby. Timetable shows the selected day. Planner shows its visible month.
- University calendar entries are informational. They do not silently cancel classes or change attendance projections.
- Remote config refreshes on resume after startup hydration. Android controls display refresh rate. Student API errors log endpoint and HTTP status only.

## Files touched

- Notification and calendar APIs, repositories, models, view models, UI, and tests.
- MainActivity, MainApp, AppHeader, remote config, and StudentApiParser.
- Existing device-ID, backend session, update UI, and version changes are now committed separately.
- README and docs/screenshots contain the refreshed UI captures.

## Validation

- Android: assembleDebug, assembleRelease, test, ktlintCheck, and detekt passed. App unit tests: 117, no failures. Core tests also passed.
- Backend: 38 tests passed; typecheck passed.
- Signed 1.1.3 build installed over the existing phone app without clearing data.
- Published signed 1.1.4 / code 12 as an optional update. Release tests, ktlintCheck, detekt, and assembleRelease passed. The downloaded APK SHA256 matches the local release. Live minimum supported code remains 10 (1.1.2).
- Live notifications loaded. Details and links display correctly. Pull-to-refresh updates timestamps. Read state survives refresh and app updates. Planner shows the September holiday.

## Open work

- Resume the download after returning from Android's unknown-app installation permission page.
- Confirm one real classroom QR submission and its final server response. Scanner access alone does not complete that check.
- The backend migration has not been deployed.
- Keep the user's AGENTS.md untracked.

## Next step

Handle the remaining update-permission resume flow. Use a real class session for final QR verification.
