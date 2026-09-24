# Lean app refactor: orchestration plan

Status: wave 2 baseline remains fixed. Agents 04 and 05 need the corrections in [the wave 2 review](reports/02-master-review.md) before acceptance. Shared API gaps are assigned to agent 06 after the wave is integrated. See [the wave 2 baseline](reports/00-wave2-baseline.md) for the original launch checks and constraints.

Use GPT-6 Sol at medium reasoning for the seven assignments below. The master planner owns sequencing, shared contracts, integration decisions, and acceptance. Implementation agents do not delegate further.

## Outcome

Keep the existing features. Move standard attendance and timetable loading out of individual screens. Show saved data promptly, prioritize visible content, share requests and results, and standardize repeated UI patterns.

Target a 10-20% net reduction in production source. This is an estimate, not a quota. Do not compress formatting, delete useful tests, remove behavior, or move duplicate code between files to meet it. Tests may grow.

## Execution order

| Wave | Agent | Assignment | Required starting point |
| --- | --- | --- | --- |
| 1 | 01 | Data foundation | Verified baseline |
| 1 | 02 | UI foundation | Verified baseline |
| 2 | 03 | Grades simplification | Integrated 01 and 02 |
| 2 | 04 | Home and attendance migration | Integrated 01 and 02 |
| 2 | 05 | Timetable and planner migration | Integrated 01 and 02 |
| 3 | 06 | Lifecycle integration and retirement of old paths | Integrated 03, 04, and 05 |
| 4 | 07 | Independent acceptance review | Completed 06 |

Run at most three agents at once. Do not start wave 2 against guessed foundation APIs. Agent 01 publishes the actual data contract. Agent 02 publishes the actual UI contract. The master integrates and checks them before releasing wave 2.

Plan files:

- [01: data foundation](01-data-foundation.md)
- [02: UI foundation](02-ui-foundation.md)
- [03: grades](03-grades.md)
- [04: home and attendance](04-home-attendance.md)
- [05: timetable and planner](05-timetable-planner.md)
- [06: integration](06-integration.md)
- [07: acceptance review](07-review.md)

All agents read [the shared contract](CONTRACT.md) before their assignment.

Path shorthand: `app/.../` means `app/src/main/kotlin/com/ash/axis/`. `core/.../` means `core/src/main/kotlin/com/ash/core/`. Report paths are relative to `docs/plans/lean-app/`.

## Baseline gate owned by the master

The initial working tree contained uncommitted edits. The completed baseline gate is recorded in `reports/00-baseline.md`. Do not start from the older main branch. Repeat this gate when preparing a later wave's shared baseline.

1. Confirm other writers have stopped. Inventory tracked and untracked changes. Preserve them. Do not reset or overwrite them.
2. Create a reviewed local baseline checkpoint that includes the intended current tracked code and these plans. Keep the user's AGENTS.md untracked. Exclude credentials, local.properties, .claude, build output, and unrelated untracked files.
3. Run the Android gate and backend tests/typecheck once. Record failures that already exist. Do not silently describe a failing baseline as verified.
4. Record the baseline commit, source-line counts, test results, and device availability in `reports/00-baseline.md`. Record production source and tests separately. Exclude generated files, vendor files, build output, and lockfiles.
5. Give each wave a shared baseline commit in isolated Git worktrees. Use `joshi/lean-01-data`, `joshi/lean-02-ui`, and equivalent branch names for later agents. A worktree from stale HEAD will lose the uncommitted work, so do not skip this gate.

This refactor does not authorize publication, deployment, version changes, or signing changes.

If isolated worktrees are unavailable, run the assignments sequentially in the shared checkout. Do not let parallel agents build or edit the same checkout.

Keep UI device access sequential even with isolated worktrees. Parallel agents must not install competing APKs or change the same emulator's state. Defer their device checks to agent 06 when needed. Limit concurrent Gradle builds to available memory; parallel coding does not require parallel full builds.

## Launch prompt

Use this prompt for each agent, substituting its assignment path and the actual baseline commit:

> Implement the assignment in `docs/plans/lean-app/01-data-foundation.md`. Read `docs/plans/lean-app/README.md`, `CONTRACT.md`, and the repository instructions first. Work from the supplied baseline commit in your assigned isolated checkout. Follow the ownership limits and prerequisite gates. Use GPT-6 Sol with medium reasoning. Do not spawn more agents. Make small local commits containing only your changes. Run the scoped checks. Write the required report. Do not merge, push, publish, or start the next assignment. Baseline commit: supplied by the master.

For agent 07, replace “Implement” with “Review” and omit permission to make code commits. Its assignment is read-only except for its report.

## Shared-file ownership

| Files | Owner |
| --- | --- |
| `data/academic/*`, attendance/timetable repositories and cache helpers | 01 |
| `core/ui/theme/*`, selected shared UI components and non-data settings presentation | 02 |
| `ui/grades/*`, grades repository/parser, grade tests | 03 |
| `ui/dashboard/*`, `ui/attendance/*`, `ui/daywise/*`, `ui/academics/*` | 04 |
| `ui/timetable/*`, `ui/planner/*` | 05 |
| `MainActivity`, `MainApp`, session lifecycle, `AppModule`, `DataRefreshSignal`, QR refresh wiring, settings mutations | 06 |

The assignment files refine these limits. New public APIs go in the owner’s contract report. A consumer reports a missing API to the master instead of modifying another agent's implementation or adding a substitute data store.

Do not assign a second agent to a shared file while its owner is running. Scope changes go through the master and are recorded in the reports.

## Integration protocol

After each wave, the master reads all reports, inspects each diff, and checks API compatibility. Integrate focused commits without rewriting unrelated history. If an interface changes, the owner updates its contract and the master informs affected agents before they continue.

Wave 1 must compile with old callers intact. Wave 2 introduces consumers; the complete app behavior is not accepted until wave 3 wires lifecycle and invalidation. Do not ship an intermediate wave.

Agent 06 runs the full integrated validation. Agent 07 independently reviews the final diff and evidence. Findings return to their original owners or agent 06, followed by targeted checks and any affected full gate.

## Reports and token budget discipline

Each agent writes `reports/NN-name.md`, normally under 100 lines:

- Baseline and resulting commit IDs.
- Files changed and public APIs added or removed.
- Behavior changed, behavior preserved, and temporary compatibility paths.
- Exact checks and results, including tests not run and why.
- Production-source and test line deltas separately.
- Known issues, blockers, and the next integration action.

Agents read only their listed starting files, direct dependencies, and relevant tests. Expand the search when needed. Do not reread the whole repository or write a second architecture proposal. Do not use live student traffic as a routine test. Use existing fakes and anonymized fixtures.

Agent 01 also writes `reports/01-data-contract.md`. Agent 02 also writes `reports/02-ui-contract.md`. Keep contracts limited to real implemented APIs and usage examples.

## Release boundary

This refactor does not establish production readiness by itself. Authentication and account isolation need explicit acceptance. The known backend identity-token signature-verification issue needs a separate security decision. Keep that issue visible; do not change backend authentication inside these assignments.
