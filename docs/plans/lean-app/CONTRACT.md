# Shared behavior contract

These are required behaviors. Agent 01 chooses the smallest concrete Kotlin APIs that satisfy them and records those APIs before consumer agents start. Do not invent an extensible framework or add a new module or dependency without a demonstrated need.

## Ownership and scope

- Repositories own reusable data and publish observable snapshots.
- A small academic-data coordinator owns active context, startup ordering, and warmup decisions.
- Domain functions own calculations. ViewModels own screen selection, filters, and presentation.
- Keep attendance and timetable snapshots independent. Do not create one app-wide state object whose every change updates all consumers.
- Keep existing account access, remote configuration, notification, calendar, grades, export, update, and QR behavior unless an assignment explicitly changes its loading trigger.
- Background means work independent of screen visibility during the app session. No periodic closed-app worker, service, polling loop, or new permission.

## Context and identity

- Snapshot account and request context for an operation. Include admission number, branch, exact-case client ID, and profile academic year wherever the endpoint requires them.
- Attendance's selected class/year and timetable's profile academic year are different concepts. Never derive the timetable year from the selected attendance semester.
- Data keys include account and all applicable class/year/range fields. Old account results must not appear in a new account's state.
- Account switch, logout, and destructive cache clearing invalidate the applicable in-memory state and pending work. A late completion must not republish invalidated data or repopulate a deliberately cleared cache.
- Preference/account changes must not combine one account's identity with another account's preferences or access token. Cancellation alone is insufficient if a response can still complete; guard publication and cache writes with context/generation checks.

## Snapshot semantics

Each dataset reports data availability, successful update time, refresh progress, and refresh error. Use one small reusable snapshot type if useful.

- `no data yet` differs from a successful empty result.
- A failed refresh retains usable saved data and its original timestamp. It must not turn a failure into an empty success.
- A screen may show content plus a refresh error. One dataset's failure must not block another dataset's successful content.
- Coroutine cancellation propagates. It is not an offline error, cached success, or retry trigger.
- Read saved data before network/profile/route discovery whenever its identity is already known. Persist enough non-secret context to reuse valid saved data on a returning launch. Do not fabricate a missing academic year on first login.

## Freshness and priorities

Use the existing `CachePolicy` fresh durations initially. Accepted stale data can appear immediately, but must schedule revalidation. Do not change durations merely to reduce request counts. Preserve deliberate offline fallback behavior and report age honestly.

- On app entry with an active account, hydrate saved core datasets. Refresh missing or stale attendance and the relevant timetable week independently.
- A fresh dataset does not need a network request merely because a tab became visible.
- Attendance opens: prioritize its selected semester. Timetable opens: prioritize its visible week. Home opens: start both core needs and publish independently.
- Day-wise opens: prioritize its requested range. Planner opens: prioritize its initial useful data before extended future coverage.
- Grades or settings opens: keep its on-demand work responsive; warm the two core datasets at lower priority.
- Start the visible demand before speculative work. A slow or failed visible request must not starve the other core dataset. Keep warmup bounded to two core dataset loads in progress, with no multi-week startup flood. Bound nested request fanout as needed; a dataset load is not necessarily one HTTP call.
- If a user navigates to a resource already loading, reuse that work. If it is queued as prefetch, promote it. Do not implement a general job scheduler for these few request types.
- Priority does not require cancelling useful in-flight work on every tab switch.
- Prefetch adjacent timetable weeks only after visible needs. Fetch additional planner ranges when used.

## Request reuse and mutations

- At most one in-flight load per full dataset key. A cancelled screen subscriber must not cancel work still needed by the active app session.
- Concurrent manual refreshes can join a valid forced load. A force refresh must not be satisfied by an old completed cache lookup.
- QR success invalidates summary and affected day-wise attendance for the originating account. Failed or ambiguous QR results do not claim refreshed attendance.
- If a pre-mutation request is already running, QR invalidation must cause a post-mutation fetch. Do not let joining old work leave the app showing a pre-scan response as fresh.
- Semester changes update attendance consumers together. Threshold and combined-attendance changes recompute presentation without fetching unchanged attendance data.
- Manual timetable refresh preserves the existing forced-profile-refresh behavior and clears relevant route-discovery memoization. Keep V1/legacy routing tests intact.

## Data boundaries

Day-wise attendance is a separate server report. Do not reconstruct it from summary counts. Calendar entries remain informational; they do not silently remove classes from projections. Preserve the existing explicit marker/holiday projection rules.

Weekly and date-keyed timetable views may share a fetched response when endpoint/context/range match. Preserve normalization semantics. Do not collapse representations by discarding date-specific changes or by assuming a weekly template is actual future data.

Grades, admit cards, PDFs, and distant historical ranges remain on demand. Notifications, configuration, and access checks retain their established triggers unless separately approved.

## UI

- Keep feature screens and local UI state. Share repeated layout and style decisions through small components and existing theme tokens.
- Avoid generic screen specifications, giant option bags, a universal ViewModel, or a component with many unrelated Boolean options.
- Preserve interaction, accessibility descriptions, text scaling, navigation state, and touch targets.
- Consistency does not justify redesigning the login, camera, calendars, and bottom navigation at once. Standardize repeated surfaces first.
- No unmeasured performance claim. A smaller file count or fewer composable functions does not prove faster rendering.

## Acceptance evidence

Use coroutine tests with controllable completion, fake time where freshness is involved, and fake APIs with request counts. Cover concurrency, failure, account switch, stale cache, and invalidation. Avoid tests that merely repeat implementation details.

Use an available emulator/device for visual and frame checks. If none is available, report the device gate as unverified. Never claim simulated UI evidence or a live QR submission that did not occur. Do not publish or silently submit real attendance to complete this refactor.
