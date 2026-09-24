# Wave 1 baseline

Verified on 2026-09-24. The master prepared this baseline after agent 01 confirmed it had made no edits and was waiting for its checkout.

## Commits

- Original main: `b763bbcdcf4223a06b40520e9fe37a40c43ce8ad`.
- Verified source checkpoint: `afcaa78311507b4707c4131e72a6a222b99ddfbb`.
- Launch baseline: the following documentation commit containing this report and the agent plans, pinned by `joshi/lean-baseline`. Resolve with `git rev-parse joshi/lean-baseline`; the master also supplies its full SHA in the launch prompt.
- The launch baseline has identical application, core, backend, build, and script content to the verified source checkpoint. Only orchestration documents are added afterward.

The source checkpoint preserves the current tracked cleanup: 75 files, 184 insertions, 710 deletions. No pre-existing source work was discarded. Other apparent modifications were Git line-ending/index status differences with no content diff.

## Validation

- `gradlew.bat :app:assembleDebug test ktlintCheck detekt --console=plain`: passed. Gradle reused 159 up-to-date tasks.
- App unit-test results: 120 passed in each of debug and release; no failures or skips.
- Core unit-test results: 26 passed in each of debug and release; no failures or skips.
- `npm test` in backend: 55 passed across 4 files.
- `npm run typecheck` in backend: passed.
- `git diff --check`: passed before checkpointing.
- `adb devices`: no connected device or emulator. Visual and performance gates remain open.

No known failing automated baseline check. This is not production or security acceptance.

## Source counts

Count physical lines in tracked `.kt` and `.ts` files. Include Android main/debug/release source. Exclude generated/vendor files, build output, lockfiles, Gradle scripts, resources, and documentation. Count tests separately. Use the same method for final comparison.

| Category | Lines |
| --- | ---: |
| App production Kotlin | 19,664 |
| Core production Kotlin | 2,307 |
| Backend production TypeScript | 846 |
| Total production source | 22,817 |
| Android tests | 2,374 |
| Backend tests | 693 |

## Isolated assignments

| Agent | Branch | Checkout |
| --- | --- | --- |
| 01 | `joshi/lean-01-data` | `C:\Coding\Axis\axis-lean-01-data` |
| 02 | `joshi/lean-02-ui` | `C:\Coding\Axis\axis-lean-02-ui` |

Both assignments start at the launch baseline. The master verifies HEAD, branch, and clean tracked state after creating the checkouts.

Each checkout receives an untracked copy of repository AGENTS.md and ignored SDK-only local.properties. These are local setup, not assignment changes. Do not commit them. Live API credentials and release-signing settings are not copied. Read-only repository instructions also apply to the worktrees.

Gradle compilation and fake-based tests do not require production credentials. Live-device flows require separately prepared local configuration and a connected device. No device installation or network mutation was performed for this baseline.

Return the reports to the master before starting wave 2. Keep `joshi/lean-baseline` fixed; integrate later work on a separate branch.
