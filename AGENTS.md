# Repository Guidelines

## Project Structure & Module Organization

Axis contains an Android client and a Cloudflare Worker backend. `app/` holds Android screens, view models, domain models, repositories, and resources. `core/` contains shared networking, storage, security, navigation, theme, and UI components. Kotlin unit tests mirror production packages under `app/src/test/` and `core/src/test/`. `backend/src/` contains the TypeScript Worker, while `backend/test/` contains Vitest tests. Database changes live in `backend/migrations/`. Keep screenshots in `docs/screenshots/`, reusable brand assets in `resources/`, and static analysis settings in `config/detekt/`.

## Build, Test, and Development Commands

- `./gradlew :app:assembleDebug` builds an installable debug APK.
- `./gradlew test` runs all JVM unit tests with JUnit 5.
- `./gradlew ktlintCheck detekt` checks Kotlin formatting and static analysis.
- `./gradlew :app:assembleDebug test ktlintCheck detekt` runs the full Android validation gate.
- `cd backend && npm install` installs Worker dependencies.
- `cd backend && npm run dev` starts Wrangler with local Cloudflare services.
- `cd backend && npm test` runs the Vitest suite.
- `cd backend && npm run typecheck` checks TypeScript without emitting files.

Use JDK 17 and Android SDK 35. Copy `local.properties.template` to `local.properties` for local configuration.

## Coding Style & Naming Conventions

Use four spaces for Kotlin and Kotlin DSL files. Keep lines at or below 140 characters. Ktlint and Detekt define the enforced Kotlin rules. Use `PascalCase` for classes, composables, and Kotlin files. Use `camelCase` for functions and properties. Name tests after the unit under test, such as `AttendanceUseCaseTest.kt`. Follow existing TypeScript style in `backend/src/`; use descriptive lower-case file names such as `session.ts`.

## Testing Guidelines

Write tests before implementation changes. Android tests use JUnit 5, MockK, Turbine, and coroutine test utilities. Backend tests use Vitest and follow `*.test.ts`. Cover changed behavior and failure paths. Run the relevant suite during development, then run the full validation gate before opening a pull request.

## Commit & Pull Request Guidelines

Use short, imperative, lower-case commit subjects, for example `separate planner projections`. Keep each commit to one logical change. Release commits may use `release 1.2.0`. Pull requests should explain the user-visible effect, list validation commands, and link related issues. Include screenshots for Compose UI changes. Call out migrations, configuration changes, and release-signing impact.

## Security & Configuration

Never commit `local.properties`, `.dev.vars`, tokens, or signing keys. Store Worker secrets with `wrangler secret put`. Keep schema changes in numbered SQL migrations. Do not edit `version.properties` manually during releases; use `scripts/release.sh`.
