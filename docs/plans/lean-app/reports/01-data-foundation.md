# Agent 01: data foundation report

- Baseline: `e672a36102f3108e35b3ee431270d318e6ebaaee`.
- Initial code commits: `8f4b57f`, `40321fe`, `cc786b2`, `61448ec`. The initial report was `75a8e11`.
- Correction commits: `4934e6f` (master acceptance tests), `0cc7850` (resource lifecycle and bounds), `afbcdfa` (independent demands and upgrade tests).
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

- Identity-safe saved data is emitted before a refresh. A successful empty response has non-null data and a timestamp. A failed refresh retains saved data and its original timestamp.
- Freshness is re-evaluated on each demand. Fresh cache skips network work. Stale and expired cache can display immediately and revalidate. Two demands for one key share a load. A later force request queues one newer load only when the active load was not forced.
- Invalidation and cache clearing reject late cache reads and writes. Cache clearing retains active flow objects so existing observers receive a later reload. QR success invalidates the selected summary and all saved day-wise ranges for the active originating account. Only the current day-wise range reloads at once. Agent 06 must wire this method to confirmed QR success.
- Attendance and timetable startup demands run independently. Profile discovery failure appears in `timetableDemandError` and does not block attendance. DAYWISE activation requires its visible range. PLANNER activation starts its initial week. OTHER starts lower-priority core warmup. Each resource permits at most two active loads; a newly visible key drops older queued keys. It does not prefetch speculative weeks. Timetable profile discovery uses the existing account-scoped academic year. It does not infer a timetable year from attendance selection.
- Forced profile refresh checks the activation generation after discovery. A result from before deactivation, reactivation, or destructive cache clearing cannot replace the current context.
- V3 timetable keys include full context and can hydrate the new week flow before route discovery. V2 attendance keys omit branch and client ID. The new observable API does not hydrate them because account ownership cannot be established. Users with only V2 attendance cache need a network load for the new path. The old adapter still reads V2 cache during this wave.
- Existing repository methods remain for old screens: attendance `getPreferredSemester`, `getSemesterOptions`, `getAcadYears`, `getClasses`, `getAttendance`, `getDaywiseAttendance`; timetable `peekTimetable`, `getTimetable`, `getDateKeyedTimetable`. Existing QR methods remain in place. Agent 06 can remove unused adapters after consumers migrate.
- Existing screen, lifecycle, DI, QR, grades, and settings files were not changed.

## Checks

- Targeted `:app:testDebugUnitTest --tests` for `WaveOneAcceptanceTest`, `AcademicResourceTest`, `AcademicDataCoordinatorTest`, `JsonCacheTest`, `AttendanceRepositoryTest`, `TimetableRepositoryTest`, and `TimetableRoutingTest`: 42 passed, 0 failed. The five cherry-picked behavioral assertions remain intact.
- `:app:compileDebugKotlin :app:ktlintCheck :app:detekt`: passed.
- `git diff --check`: passed.
- No device or emulator was available in the baseline report. No live student request or device check was run. Full Android and backend gates belong to integration.

## Size and handoff

- App production Kotlin: +739 physical lines against baseline, +126 in this correction. Test Kotlin: +889 lines against baseline, +427 in this correction. Other production and test categories: unchanged.
- The data foundation adds code now because old paths remain during wave 1. Agent 06 owns retiring unused adapters and measuring the integrated result.
- Agent 03 can use `JsonCache` for grades. Agents 04 and 05 should consume the concrete APIs in `01-data-contract.md`.
- Agent 06 must call `activate` after account and selection are coherent, `deactivate` at switch/logout, `clearAcademicCache` for destructive clearing, and `qrSucceeded` only after a confirmed scan.
- No account lifecycle or UI consumer is wired in this wave. The device and integrated behavior gates remain open. Agent 06 should call `activate` from an app-session coroutine so slow profile discovery does not hold the UI.
