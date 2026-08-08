# Axis backend

A tiny [Cloudflare Worker](https://developers.cloudflare.com/workers/) that serves **remote config** to the
Axis Android app. It exists so we can change two things *without shipping an APK*:

- **`authToken`** — the static iCloudEMS bearer the app sends to authorize OTP / login / refresh. iCloudEMS
  rotates this occasionally; when it does, logins break for everyone until a new build ships. With this
  service we update one KV value and every client recovers on its next launch.
- **`appVersion`** — the `appversion` string the app sends to iCloudEMS (same rotation risk).

It can also **kill-switch** the app or enforce a **minimum version** in an emergency.

> The app treats this service as *additive*: `authToken` is optional, and if the endpoint is unreachable or
> unset the app keeps using its compiled-in `BuildConfig` token. Nothing here is required for the app to run —
> it's a safety valve.

## The contract — `GET /v1/config`

```jsonc
{
  "authToken": "…",              // optional; omitted → app uses its baked-in token
  "appVersion": "3.0.3",
  "minSupportedVersionCode": 1,  // app blocks if BuildConfig.VERSION_CODE < this
  "latestVersionCode": 1,        // for a soft "update available" nudge
  "latestVersionName": "1.0.0",
  "updateUrl": "",
  "killSwitch": false,           // true → app shows `message` and blocks use
  "message": "",
  "updatedAt": "1970-01-01T00:00:00.000Z"
}
```

Responses carry a weak `ETag`; send it back as `If-None-Match` to get a `304`. Cached `max-age=300`.
Config is stored per tenant in KV under `config:<tenant>`; add `?tenant=gu` to target one (defaults to
`DEFAULT_TENANT`).

## Routes

| Method | Path            | Auth                              | Purpose                          |
| ------ | --------------- | --------------------------------- | -------------------------------- |
| GET    | `/healthz`      | none                              | liveness probe                   |
| GET    | `/v1/config`    | `x-axis-key` *(only if set)*      | fetch config                     |
| PUT    | `/v1/config`    | `Authorization: Bearer <ADMIN>`   | merge a partial config and save  |
| POST   | `/v1/session`   | iCloudEMS token in body           | register/refresh a user, get status + role |
| POST   | `/v1/events`    | `Bearer <any Axis session>`       | bump aggregate usage counters    |
| GET    | `/v1/admin/users` | `Bearer <Axis session, admin>`  | list governed users (with usage) |
| POST   | `/v1/admin/users/:admno/allow` \| `/kick` \| `/ban` | `Bearer <Axis session, admin>` | approve / revoke / hard-block a user |
| POST   | `/v1/admin/approve-all` | `Bearer <Axis session, admin>` | approve every pending user    |
| GET    | `/v1/admin/health` | `Bearer <Axis session, admin>` | counts, event metrics, APK status |
| GET    | `/v1/admin/config` | `Bearer <Axis session, admin>` | read config from the Admin page   |
| PUT    | `/v1/admin/config` | `Bearer <Axis session, admin>` | edit config from the Admin page (force-update, kill-switch, …) |
| GET    | `/v1/apk`       | `x-axis-key` *(only if set)*      | download the latest APK *(R2 only)* |
| PUT    | `/v1/admin/apk` | `Bearer <admin session or ADMIN>` | upload a new APK *(R2 only)*      |

**allow / kick / ban:** *allow* → approved; *kick* → pending (a re-login re-requests approval); *ban* → banned
(a hard block that survives re-login). Admins can't be kicked or banned. Config also carries a `notice`
(non-blocking in-app banner) and `autoApprovePrefix` (comma-separated admno prefixes that auto-approve on first
sight — a middle ground between approve-each and full `OPEN_ENROLLMENT`).

`PUT` is a **merge**: send only the keys you want to change. `authToken: null` (or `""`) clears the override.
There are two ways to edit config: `PUT /v1/config` with the deploy-time `ADMIN_TOKEN` (CLI/CI), or
`PUT /v1/admin/config` with an **admin Axis session** — that's what the app's Admin page uses, so the owner
sets the force-update floor from their phone. See [User governance](#user-governance-approve--kick) below.

## First-time setup

```bash
cd backend
npm install

# 1. Log in to Cloudflare
npx wrangler login

# 2. Create the KV namespace, then paste the printed id into wrangler.toml (kv_namespaces.id)
npx wrangler kv namespace create CONFIG

# 3. Create the D1 database, then paste the printed database_id into wrangler.toml (d1_databases.database_id)
npx wrangler d1 create axis
npx wrangler d1 migrations apply axis --remote   # applies all migrations (users table + usage columns)

# 4. Set secrets (prompts for the value; never commit these)
npx wrangler secret put ADMIN_TOKEN          # long random string — required to write config
npx wrangler secret put SESSION_SECRET       # long random string — signs Axis session tokens
npx wrangler secret put ADMIN_ADMNOS         # your admno(s), comma-separated — the owner/admin
npx wrangler secret put DEFAULT_AUTH_TOKEN   # optional: seed the iCloudEMS bearer
npx wrangler secret put APP_ACCESS_KEY       # optional: soft-gate GET with an x-axis-key header

# 5. Ship it
npm run deploy
```

Your Worker is now at `https://axis-backend.<your-subdomain>.workers.dev`.

## Hot-fixing the bearer (the whole point)

When iCloudEMS rotates the token, from anywhere:

```bash
curl -X PUT https://axis-backend.<subdomain>.workers.dev/v1/config \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "content-type: application/json" \
  -d '{"authToken": "<new-bearer>"}'
```

Emergency stop / force-update:

```bash
# Block the app with a message
curl -X PUT …/v1/config -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"killSwitch": true, "message": "Axis is down for maintenance — back shortly."}'

# Require everyone on build code < 5 to update
curl -X PUT …/v1/config -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"minSupportedVersionCode": 5, "updateUrl": "https://…"}'
```

## User governance (approve / kick)

Controls **who may use your Axis distribution**. iCloudEMS stays the identity provider (it proves a caller is
really student `admno`); this backend is the *authorization authority* (pending / approved, user / admin).

Flow: after iCloudEMS login the app calls `POST /v1/session` with its iCloudEMS access token. The Worker reads
the `admno` from that token (light validation — decode + expiry, no signature check), upserts the user, and:

- a **new** admno → `pending` (unless `OPEN_ENROLLMENT="true"`, or it's an owner in `ADMIN_ADMNOS`);
- **`approved`** → returns a short-lived **Axis session token** (`sessionToken`) carrying `admno` + `role`;
- an **owner** (`ADMIN_ADMNOS`) → always `approved` + `admin`.

```jsonc
// POST /v1/session   { "token": "<iCloudEMS access token>" }
{ "status": "approved", "role": "admin", "admno": "…", "name": "…", "sessionToken": "…" }
```

Admin actions use that `sessionToken` (role must be `admin`):

```bash
# List everyone (pending first)
curl https://…/v1/admin/users -H "Authorization: Bearer $AXIS_SESSION"

# Allow (approve) or Kick (revoke → back to pending)
curl -X POST https://…/v1/admin/users/<admno>/allow -H "Authorization: Bearer $AXIS_SESSION"
curl -X POST https://…/v1/admin/users/<admno>/kick  -H "Authorization: Bearer $AXIS_SESSION"
```

A kicked user loses access on their next launch; if they re-login they return as `pending`. Admins can't be
kicked. **Rollout:** flipping this on makes existing users `pending` — set `OPEN_ENROLLMENT="true"` in
`wrangler.toml` during rollout to auto-approve, then set it back to `"false"`.

**Limits:** enforcement is app-side (a modified APK or direct iCloudEMS use bypasses it), and light validation
is spoofable by a hand-crafted token — acceptable for a student app, hardenable later with a server-side probe.

**Usage telemetry.** Each `POST /v1/session` also reports the caller's app version, device model, Android SDK,
and bumps a launch counter. `GET /v1/admin/users` returns these so the Admin page can show who runs which build
(which drives the force-update decision), on what device, and how active they are.

## One-tap updates (optional, self-hosted APK)

The app can download and install a new APK in place (system installer, no browser). It just needs a direct
`.apk` link in `updateUrl` — host it **anywhere** (GitHub Releases, any static host), or serve it straight from
this Worker via R2:

```bash
# Enable R2 once, create the bucket, and uncomment the [[r2_buckets]] block in wrangler.toml
npx wrangler r2 bucket create axis-apk
npm run deploy

# Upload a freshly built, signed release APK (admno-less CI auth via ADMIN_TOKEN)
curl -X PUT "https://…/v1/admin/apk?versionCode=2&versionName=1.1.0" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  --data-binary @app-release.apk

# Point the app at it, and advertise the new build
curl -X PUT https://…/v1/config -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"updateUrl":"https://…/v1/apk","latestVersionCode":2,"latestVersionName":"1.1.0"}'
```

Then `latestVersionCode > installed` shows a dismissible "Update" prompt; raising `minSupportedVersionCode`
forces it. Until an R2 bucket is bound the `/v1/apk` routes return `503` (the Worker still deploys fine).
**Auto-updates require every distributed APK to be signed with the same key** — a signature mismatch is rejected.

## Local development

```bash
cp .dev.vars.example .dev.vars                        # fill in ADMIN_TOKEN, SESSION_SECRET, ADMIN_ADMNOS
npx wrangler d1 migrations apply axis --local         # apply all migrations to the local users table
npm run dev                                           # wrangler dev with simulated local KV + D1
npm run typecheck                                     # tsc --noEmit
npm test                                              # vitest — config + session + governance logic
```

## Security notes

- The `authToken` is already extractable from the APK, so serving it here is no worse — but set
  `APP_ACCESS_KEY` to stop trivial scraping of `/v1/config`.
- Config writes are disabled until `ADMIN_TOKEN` is set (`PUT` returns `503`); sessions/admin are disabled
  until `SESSION_SECRET` is set. Keep both out of the repo.
- Axis session tokens are signed HS256 with `SESSION_SECRET`; admin endpoints re-check the caller is still an
  admin in D1 on every call.

## Next (roadmap, not built yet)

Cross-device account sync and push notifications (low-attendance / timetable / grades), layered on this same
Worker later.
