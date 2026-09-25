# Agent 01: implemented data contract

## Ownership and state

- `AcademicDataCoordinator` is an injectable singleton. Its constructor takes `AttendanceRepository`, `TimetableRepository`, and `AuthRepository`. Construction starts no work.
- `activeContext: StateFlow<StudentRequestContext?>` publishes the resolved profile year when it becomes available and becomes null on deactivation.
- `selectedSemester: StateFlow<AcademicSemesterSelection>` carries the active context, selected option, metadata loading, and metadata error together. Saved year/class IDs bind immediately. `discoverSemester(savedYear, savedClass)` fills the label or discovers a missing selection. An old discovery cannot replace a manual or new-account selection.
- `transition: StateFlow<Long>` advances on activation, deactivation, destructive cache clearing, and a changed profile year. Consumers use it to reject suspended projection work and clear derived data.
- `attendanceDemandError`, `timetableDemandError`, and `daywiseDemandError` are `StateFlow<Throwable?>` values for failures before a repository snapshot can publish, such as profile discovery or cache access. They clear on a successful later demand, activation, and deactivation.
- The two repositories are injectable singletons and own their independent observable `StateFlow`s and in-flight loads. The coordinator owns active context and startup demand, not network parsing or cache policy.
- Call `activate(context, selectedSemester, weekStart, destination)` once after account and preference values belong to the same account. Call `deactivate()` before switching accounts or logging out. Call `clearAcademicCache()` for destructive cache clearing.
- `StudentRequestContext(admno, brId, clientId, academicYear)` carries exact-case client ID and profile timetable year. A blank academic year triggers `AuthRepository.requireStudentRequestContext()` before timetable network work. Attendance uses `SemesterOption.yearId` independently.

## Keys and snapshots

- `AttendanceKey(context, classId, year)` keys summary by account, branch, exact client ID, selected class, and selected attendance year.
- `DaywiseKey(context, year, fromDate, toDate)` adds the server report range.
- `TimetableKey(context, startDate, endDate)` keys the requested timetable range and profile academic year.
- `AcademicSnapshot<T>(data, updatedAtMillis, refreshing, error, freshness)` is the value of each flow. `data == null` means no successful result. An empty collection inside non-null data is a successful empty result. A failure leaves saved data and timestamp intact and sets `error`. `freshness` is `FRESH`, `STALE`, or `EXPIRED` for cache data; a network success is `FRESH`.
- `TimetableData.weekly` keeps day-name slots. `dated` keeps date-specific slots. `dated == null` only for legacy weekly-only cache hydration; an empty map from a fetched response is a valid empty date result.
- Accepted stale and expired cache appears before network refresh. Freshness is read again on each demand. A force call joins an existing forced load. A force call during an ordinary load schedules one newer load. Cancelling a collector does not cancel repository work. Resource cancellation propagates and does not become an offline result.
- Cache clearing resets snapshots but retains their `StateFlow` objects for active observers. Deactivation clears account state and rejects old work. Reads and writes are guarded by entry identity and a resource-wide invalidation epoch.
- At most two loads per resource run at once. A new visible key cancels queued speculative timetable work, while required planner coverage remains queued. One coordinator worker drains required weeks before adjacent prefetch, leaving a resource permit for visible work. Useful in-flight loads continue. Startup makes no speculative week requests. QR invalidation removes all saved day-wise ranges for the account and reloads only the latest visible range; historical ranges reload when demanded.
- Semester metadata class requests have a concurrency limit of two. Metadata cache keys include admission number, branch, and exact-case client ID. QR submission verifies the active account after token refresh and before submission.
- A confirmed QR result received before session activation is retained for that account. Activation invalidates daily ranges and invalidates the selected summary when its key becomes known. A result from a different active account is ignored.

## Methods

- Coordinator: `activate(context, selectedSemester, weekStart, destination = HOME, visibleDaywise = null)`, `deactivate()`, `selectSemester(option)`, `discoverSemester(savedYear = "", savedClass = "")`, `attendanceVisible()`, `timetableVisible(weekStart)`, `homeVisible(weekStart = currentMonday)`, `otherVisible()`, `daywiseVisible(year, fromDate, toDate)`, `plannerVisible(weekStart)`, `plannerCoverage(weeks)`, `prefetchAdjacent(weekStart)`, `refreshAttendance()`, `refreshDaywise(year, fromDate, toDate)`, `refreshTimetable()`, `qrSucceeded(origin)`, `clearAcademicCache()`.
- `AcademicDestination`: `HOME`, `ATTENDANCE`, `TIMETABLE`, `DAYWISE`, `PLANNER`, `OTHER`. `DAYWISE` needs `DaywiseRange(year, fromDate, toDate)` in `visibleDaywise`. `PLANNER` uses `weekStart` as its initial visible week. `OTHER` starts bounded core warmup asynchronously. Visible academic demand begins before core warmup; both core datasets proceed independently.
- Attendance repository: `observeSummary(key)`, `requestSummary(key, force = false)`, `observeDaywise(key)`, `requestDaywise(key, force = false)`. All return `StateFlow<AcademicSnapshot<...>>`. `invalidateSummary(key)` and `invalidateDaywiseForAccount(context)` are available for mutation wiring.
- Timetable repository: `observeWeek(key)`, visible `requestWeek(key, force = false)`, `requestRequiredWeek(key)`, and `requestPrefetchWeek(key)` return `StateFlow<AcademicSnapshot<TimetableData>>`. `clearRoute(context)` and `invalidateWeek(key)` remain available for explicit invalidation.
- Coordinator manual timetable refresh calls `requireStudentRequestContext(forceProfileRefresh = true)` and then forces the week load. It clears route memoization. QR invalidation accepts only the originating active account.

## Consumer examples

```kotlin
coordinator.activate(context, semester, monday, AcademicDestination.HOME)
val summary = attendanceRepository.observeSummary(AttendanceKey(context, semester.classId, semester.yearId))
```

```kotlin
coordinator.attendanceVisible() // selected semester first, current week afterward
coordinator.selectSemester(otherSemester) // reloads only the new summary key
coordinator.activate(context, otherSemester, monday, AcademicDestination.DAYWISE,
    DaywiseRange(otherSemester.yearId, fromDate, toDate))
```

```kotlin
coordinator.timetableVisible(monday)
val resolved = coordinator.activeContext.value ?: return
val week = timetableRepository.observeWeek(TimetableKey(resolved, monday.toString(), monday.plusDays(6).toString()))
// Use week.value.data?.weekly for timetable or week.value.data?.dated for planner dates.
coordinator.plannerVisible(currentMonday) // planner's core current week
coordinator.plannerCoverage(usedWeeks) // required month and projection coverage, scheduled one at a time
```

```kotlin
coordinator.otherVisible() // call after grades or settings starts its own work
coordinator.qrSucceeded(originContext) // confirmed scan only
coordinator.deactivate() // account switch or logout
```

## Typed JSON cache for grades

```kotlin
val cache = JsonCache(cacheDao, json)
val saved: CachedResult<Grades>? = cache.read(key, Grades.serializer(), CachePolicy.DASHBOARD)
val accepted = cache.readAccepted(key, Grades.serializer(), CachePolicy.DASHBOARD)
val updatedAtMillis = cache.write(key, grades, Grades.serializer())
```

`read` returns valid data at any age with its original timestamp and freshness. `readAccepted` excludes `EXPIRED`. Repository policy decides when to refresh and whether to show older offline data. `JsonCache` does not catch network errors or own request work.

V3 timetable cache contains admission number, branch, exact-case client ID, profile year, route, and range. The new flow can hydrate it. V2 attendance cache omits branch and client ID. The shared flow intentionally does not read V2 entries; ownership cannot be proved from that key. Export-only attendance and dated timetable methods retain their existing on-demand fallback and formats. The removed V2 day-wise adapter has no remaining caller.
