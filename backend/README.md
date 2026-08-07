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

`PUT` is a **merge**: send only the keys you want to change. `authToken: null` (or `""`) clears the override.

## First-time setup

```bash
cd backend
npm install

# 1. Log in to Cloudflare
npx wrangler login

# 2. Create the KV namespace, then paste the printed id into wrangler.toml (kv_namespaces.id)
npx wrangler kv namespace create CONFIG

# 3. Set secrets (prompts for the value; never commit these)
npx wrangler secret put ADMIN_TOKEN          # long random string — required to write config
npx wrangler secret put DEFAULT_AUTH_TOKEN   # optional: seed the iCloudEMS bearer
npx wrangler secret put APP_ACCESS_KEY       # optional: soft-gate GET with an x-axis-key header

# 4. Ship it
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

## Local development

```bash
cp .dev.vars.example .dev.vars   # fill in ADMIN_TOKEN etc.
npm run dev                      # wrangler dev with a simulated local KV
npm run typecheck                # tsc --noEmit
npm test                         # vitest — pure config logic
```

## Security notes

- The `authToken` is already extractable from the APK, so serving it here is no worse — but set
  `APP_ACCESS_KEY` to stop trivial scraping of `/v1/config`.
- Writes are disabled until `ADMIN_TOKEN` is set (`PUT` returns `503`). Keep that token out of the repo.

## Next (roadmap, not built yet)

Account sync across devices and Axis-user governance (approve/ban) — both need a DB (Cloudflare D1) and
per-user auth, layered on this same Worker later.
