# Agent 01: data foundation report

- Baseline: `e672a36102f3108e35b3ee431270d318e6ebaaee`.
- Code commits: `8f4b57f` (typed cache), `40321fe75a91b527047ac71c95c8586355c589ba` (academic loads), `cc786b2139a41b5422a6f19a2f335a5bc4568729` (resolved context), `61448ec517761ebb7a7ff22b61ef248eba322977` (legacy fallback test).
- Branch: `joshi/lean-01-data`. This report is a separate local commit.

## Changes

- Added `data/db/JsonCache.kt` and two exact-prefix/delete operations in `CacheDao.kt`.
- Refactored `AttendanceCacheStore.kt` and `TimetableCacheStore.kt` to use the typed helper.
- Added `data/academic/AcademicResource.kt` and `AcademicDataCoordinator.kt`.
- Added observable summary and day-wise loads to `AttendanceRepository.kt`. New requests use exact-case client IDs and full account, class, year, and range keys.
- Added observable timetable loads to `TimetableRepository.kt`. One response produces weekly and date-keyed views. Route discovery is memoized per request context and cleared on force refresh or deactivation.
- Added focused tests under `data/academic/`, `data/db/`, and the attendance/timetable repository tests. The coordinator publishes the resolved profile context for consumers.
- Public APIs are listed in `01-data-contract.md`.

## Behavior and compatibility

- Saved data is emitted before a refresh. A successful empty response has non-null data and a timestamp. A failed refresh retains the saved data and original timestamp.
- Fresh cache skips network work. Stale and expired cache can display immediately and revalidate. Two demands for one key share a load. A later force request queues one newer load only when the active load was not forced.
- Invalidation and cache clearing reject late writes. QR success can invalidate the selected summary and all saved day-wise ranges for the active originating account. Agent 06 must wire this method to confirmed QR success.
- Attendance and timetable loads are independent. The coordinator starts visible demand before other core work. It does not prefetch speculative weeks. Timetable profile discovery uses the existing account-scoped academic year. It does not infer a timetable year from attendance selection.
- Existing repository methods remain for old screens: attendance `getPreferredSemester`, `getSemesterOptions`, `getAcadYears`, `getClasses`, `getAttendance`, `getDaywiseAttendance`; timetable `peekTimetable`, `getTimetable`, `getDateKeyedTimetable`. Existing QR methods remain in place. Agent 06 can remove unused adapters after consumers migrate.
- Existing screen, lifecycle, DI, QR, grades, and settings files were not changed.

## Checks

- Targeted `:app:testDebugUnitTest --tests` for `AcademicResourceTest`, `AcademicDataCoordinatorTest`, `JsonCacheTest`, `AttendanceRepositoryTest`, `TimetableRepositoryTest`, and `TimetableRoutingTest`: 29 passed, 0 failed.
- `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`: passed.
- `git diff --check`: passed.
- No device or emulator was available in the baseline report. No live student request or device check was run. Full Android and backend gates belong to integration.

## Size and handoff

- App production Kotlin: +613 physical lines against baseline. Test Kotlin: +462 lines. Other production and test categories: unchanged.
- The data foundation adds code now because old paths remain during wave 1. Agent 06 owns retiring unused adapters and measuring the integrated result.
- Agent 03 can use `JsonCache` for grades. Agents 04 and 05 should consume the concrete APIs in `01-data-contract.md`.
- Agent 06 must call `activate` after account and selection are coherent, `deactivate` at switch/logout, `clearAcademicCache` for destructive clearing, and `qrSucceeded` only after a confirmed scan.
- No account lifecycle or UI consumer is wired in this wave. The device and integrated behavior gates remain open.
