# Wave 2 launch baseline

Verified on 2026-09-24. Agents 03, 04, and 05 may start in parallel from this baseline in their assigned worktrees.

## Commits

- Original baseline: `e672a36102f3108e35b3ee431270d318e6ebaaee`. Keep `joshi/lean-baseline` unchanged.
- Reviewed data submission: branch `joshi/lean-01-data`, ending at `b8bb23b`.
- UI commits were already integrated as `a848836` and `42d1c63`.
- Integrated source checked here: `a87e68ce8701d1654651715b597190b0a31e928f` on `joshi/lean-integration`.
- Launch baseline: the following documentation-only commit containing this report, pinned by `joshi/lean-wave2-baseline`. Resolve its full SHA with `git rev-parse joshi/lean-wave2-baseline`.

## Validation

- `gradlew.bat :app:assembleDebug test ktlintCheck detekt --console=plain`: passed, 159 tasks, 60 executed and 99 up to date.
- App tests: 151 passed in each of debug and release. Core tests: 26 passed in each variant. No failures, errors, or skips.
- All five master regression tests pass in the integrated build. The original behavioral assertions remain intact.
- Backend `npm test`: 55 passed. `npm run typecheck`: passed.
- `git diff --check`: passed.
- `adb devices`: no connected device. Visual and performance checks remain open.

The full integrated gate supersedes the failed tests recorded in the initial master review. Foundation acceptance permits consumer implementation. It does not establish completed lifecycle behavior or production readiness.

## Consumer constraints

- Read the corrected `01-data-contract.md` and `02-ui-contract.md`. Do not use the initial data contract from before the correction.
- Coordinator activation belongs to agent 06. Consumers declare visible demand and observe snapshots; they must not repeatedly activate the session themselves.
- Observe resolved `activeContext` changes when binding timetable keys. Do not capture a blank profile year once and retain that key forever.
- The foundation bounds loads per resource and drops older queued keys when a new visible key is prioritized. It does not provide a separate speculative-prefetch API. Agent 05 must report an API gap if adjacent prefetch or multiple required planner ranges cannot be expressed safely. Do not disguise speculative requests as visible navigation or silently accept missing coverage.
- Treat the overall priority and concurrency contract as an integrated acceptance gate for agents 06 and 07. A per-resource permit count alone does not prove the global contract.
- V2 attendance cache omits branch and client identity. The new path intentionally cannot reuse it safely. Upgrading users with only V2 saved attendance need one successful network load. Preserve account isolation. V3 timetable cache retains full identity and can hydrate the new flow.
- Lifecycle, QR wiring, removal of legacy loading paths, device checks, and the separate backend authentication review remain open.

## Counts

Same physical-line method as the original baseline, including main/debug/release Kotlin and excluding generated/vendor/build output.

| Category | Original | Wave 2 |
| --- | ---: | ---: |
| App production Kotlin | 19,664 | 20,381 |
| Core production Kotlin | 2,307 | 2,361 |
| Backend production TypeScript | 846 | 846 |
| Total production | 22,817 | 23,588 |
| Android tests | 2,374 | 3,263 |
| Backend tests | 693 | 693 |

The temporary production increase is 771 lines. Old callers still exist. Later agents must remove replaced responsibilities rather than compress formatting to meet a line target.

## Isolated assignments

| Agent | Branch | Checkout |
| --- | --- | --- |
| 03 | `joshi/lean-03-grades` | `C:/Coding/Axis/axis-lean-03-grades` |
| 04 | `joshi/lean-04-home-attendance` | `C:/Coding/Axis/axis-lean-04-home-attendance` |
| 05 | `joshi/lean-05-timetable-planner` | `C:/Coding/Axis/axis-lean-05-timetable-planner` |

Each receives untracked AGENTS.md and ignored SDK-only local.properties. Do not commit either. No credentials or signing configuration are copied. Keep builds sequential if memory is constrained. Return reports and focused local commits to the master; do not merge or publish.
