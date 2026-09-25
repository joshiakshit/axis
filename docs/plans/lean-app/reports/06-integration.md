# Agent 06: lifecycle integration

Baseline: `97a754674b3c37c95b9ea6703f46020089fe98db` on `joshi/lean-06-integration`. Code commits: `313fc48` (shared API) and `9f7d0ed` (lifecycle and consumers). The final documentation commit SHA is returned with this report. The untracked `AGENTS.md` and ignored `local.properties` were left out of commits.

## Architecture and behavior

- `SessionGate` starts `SessionViewModel` after the splash. The session reads fixed account-scoped preference keys, activates `AcademicDataCoordinator`, and declares the initial route. Later route changes declare Home, Academics, Timetable, or other visible demand. Account switching and logout deactivate before changing the active account.
- The coordinator publishes account-bound `selectedSemester` and `activeContext`. Saved year/class IDs bind before optional label discovery. Discovery, manual selection, profile resolution, errors, and late results use generation or key checks. Home requests its real current week. The timetable keeps a separate selected viewport. The Academics planner subtab requests current-week core data without changing that viewport.
- Visible timetable requests use the repository's promoted path. A single background worker drains all required planner weeks before adjacent prefetch. The resource runs at most two timetable loads. Planner retains continuous observers for its displayed month and one sequential observer for distant projection weeks. Requested horizons are no longer cut off at 32 weeks.
- Settings selection updates the shared state after writing both fixed account keys. Cache clearing resets repository snapshots and planner projections. Confirmed QR success invalidates the originating account's summary and daily ranges, including success before session activation. QR submission checks the active account after token refresh. Late old-account results cannot update the new account's UI.
- Repositories still own cache and network snapshots. `AcademicResource` rejects writes after invalidation and publishes cache-read errors on the keyed snapshot. Semester metadata cache keys include admission number, branch, and exact-case client ID. Metadata class requests run two at a time.

## Removed and retained paths

- Removed `DataRefreshSignal`, per-screen `SemesterSelection` discovery, the unused V2 day-wise adapter, direct planner `requestWeek` fanout, the 32-week presentation limit, and planner test `Thread.sleep` polling.
- Kept export-only attendance and dated timetable methods. They preserve the existing CSV, PDF, and ICS formats and offline fallback. Kept the legacy timetable route method used by route behavior tests. No backend, token storage, signing, or release version changed.

## Validation

- `gradlew.bat :app:assembleDebug test ktlintCheck detekt :app:compileReleaseKotlin --console=plain`: passed. App tests: 196 passed in debug and 196 in release. Core tests: 26 passed in debug and 26 in release. These are 222 distinct tests run in both variants, with no failures, errors, or skips.
- Backend `npm test`: 55 passed. `npm run typecheck`: passed. `npm ci` installed the worktree's locked dependencies before these checks. Backend source is unchanged.
- Controlled tests cover saved startup data, independent resource failure, request reuse, account and cache transitions, semester discovery races, QR invalidation during a load and before activation, Home week selection, required planner coverage, and suspended projection rejection. `git diff --check`: passed.
- `adb devices`: no connected device. Visual, accessibility, export viewing/sharing, QR camera, repeated latency, frame timing, and same-device performance comparison are unverified. No live attendance was submitted. No screenshots or performance improvement claims are made.

## Source lines against the original baseline

Physical Kotlin and TypeScript lines. Production includes Android main/debug/release and backend `src`. Tests include Android `src/test` and backend `test`. Generated files, dependencies, build output, and lockfiles are excluded.

| Category | Original `e672a361` | Final | Delta |
| --- | ---: | ---: | ---: |
| App production Kotlin | 19,664 | 21,058 | +1,394 |
| Core production Kotlin | 2,307 | 2,361 | +54 |
| Backend production TypeScript | 846 | 846 | 0 |
| Total production | 22,817 | 24,265 | +1,448 |
| Android tests | 2,374 | 5,153 | +2,779 |
| Backend tests | 693 | 693 | 0 |
| Total tests | 3,067 | 5,846 | +2,779 |

The measured production total is above the initial 10–20% reduction estimate. It preserves the required behavior and tests. Device acceptance remains open. The existing backend identity-token signature-verification concern remains a separate security decision.
