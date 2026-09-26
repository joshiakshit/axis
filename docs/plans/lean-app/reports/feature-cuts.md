# Feature cuts

Base: `af21f6273ada8bb1a394536650544ce5ea8d5d70`.
Checkout: `C:/Coding/Axis/axis-lean-feature-cuts`, branch `joshi/lean-feature-cuts`.

The user first removed Result, Admit Card, and notifications. The next request removes all Grades, account switching, custom accents, manual semester selection, non-PNG exports, Clear Cache, and the old support section. These requests supersede earlier preservation requirements for these features.

- Removed all Grades UI, requests, parsing, models, and obsolete tests. A saved Grades start route falls back to Home.
- Removed notification navigation, header badge, resume sync, repository, API method, models, and obsolete tests.
- Kept the calendar's shared status component. Attendance and viewed-week timetable exports now share/save PNG only. Images wrap full text. Removed CSV, ICS, and PDF generation.
- Academics remains the Overall, Day-wise, and Planner tab container.
- Retained preset colours: Slate, Azure, Teal, Emerald, Amber, Coral, Rose, Violet. Previously saved custom colours fall back to Slate.
- Startup discovers the latest semester. Saved manual overrides no longer affect attendance or exports. Removed the picker and manual selection API.
- Removed Clear Cache from Settings. Logout still clears private cached data.
- Updates show a reload icon beside Update, progress, retryable failures, and installation controls. Repeated taps do not duplicate checks.
- Bugs and fixes opens https://github.com/joshiakshit/axis.
- Removed account switching and saved account lists. Upgrade preserves active credentials and deletes alternate credentials. Logout cannot select another saved account.
- Cloudflare session requests no longer include deviceId. University OTP/login retain their required device identifier. Worker source and existing cloud database records are unchanged.

Physical source lines, including blank lines and comments:

| Area | Agent 06 | First cuts | Current |
| --- | ---: | ---: | ---: |
| Production Kotlin and TypeScript | 24,265 | 22,115 | 18,984 |
| Tests | 5,846 | 5,502 | 5,478 |

Current Settings presentation: 995 lines. Export implementation: 237 lines. Production decreased by 3,131 lines this round and 5,281 lines across both rounds. These savings include explicitly removed features.

Android gate: debug assembly, debug/release tests, ktlint, detekt, and release Kotlin compilation passed. There are 209 distinct Android tests, each passing in debug and release: 179 app tests and 30 core tests per variant. Checks cover credential migration/logout, preset fallback, automatic semester discovery, late discovery responses, PNG content and viewed-week selection, absence of deviceId in the session payload, and update failure/retry/deduplication. Backend files are unchanged; backend checks were not rerun. `git diff --check` passed.

No device is connected. Layout, accessibility, PNG raster rendering, file sharing/saving, and performance checks remain unverified on a device. PNG unit tests verify export content and routing with the Android renderer mocked. Changes are local and uncommitted.
