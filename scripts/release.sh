#!/usr/bin/env bash
#
# One-command release: bump the version, build the signed APK, upload it to the Axis Worker (R2), and flip the
# "latest build" config so every installed app offers the update. No more manual editing.
#
#   scripts/release.sh                 # auto-bump patch (1.0.0 -> 1.0.1), advertise as the latest build
#   scripts/release.sh 1.2.0           # set an explicit versionName, advertise
#   scripts/release.sh --force         # ALSO raise the force-update floor (blocks old builds until updated)
#   scripts/release.sh 2.0.0 --force   # both
#   scripts/release.sh --check         # run the full test/lint gate before building
#
# Requires in local.properties:  REMOTE_CONFIG_URL, ADMIN_TOKEN  (and the RELEASE_* signing keys for a real
# distributable build). Requires an R2 bucket bound in the Worker (see backend/README.md → One-tap updates).

set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" local.properties 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }

BASE="$(prop REMOTE_CONFIG_URL)"; BASE="${BASE%/}"
ADMIN_TOKEN="$(prop ADMIN_TOKEN)"
[ -n "$BASE" ]        || { echo "✗ REMOTE_CONFIG_URL missing from local.properties"; exit 1; }
[ -n "$ADMIN_TOKEN" ] || { echo "✗ ADMIN_TOKEN missing from local.properties (wrangler secret value)"; exit 1; }

# --- args ---------------------------------------------------------------------------------------------
FORCE=0; CHECK=0; NEW_NAME=""
for a in "$@"; do
  case "$a" in
    --force) FORCE=1 ;;
    --check) CHECK=1 ;;
    -*)      echo "unknown flag: $a"; exit 1 ;;
    *)       NEW_NAME="$a" ;;
  esac
done

# --- bump version.properties --------------------------------------------------------------------------
OLD_CODE="$(grep -E '^VERSION_CODE=' version.properties | cut -d= -f2- | tr -d '\r')"
OLD_NAME="$(grep -E '^VERSION_NAME=' version.properties | cut -d= -f2- | tr -d '\r')"
NEW_CODE=$(( OLD_CODE + 1 ))
if [ -z "$NEW_NAME" ]; then
  IFS=. read -r MA MI PA <<<"$OLD_NAME"
  NEW_NAME="${MA:-1}.${MI:-0}.$(( ${PA:-0} + 1 ))"   # auto patch-bump
fi
printf 'VERSION_CODE=%s\nVERSION_NAME=%s\n' "$NEW_CODE" "$NEW_NAME" > version.properties
echo "▸ version  $OLD_NAME ($OLD_CODE) → $NEW_NAME ($NEW_CODE)"

# --- build ---------------------------------------------------------------------------------------------
if [ "$CHECK" = 1 ]; then
  echo "▸ running test + lint gate…"
  ./gradlew :app:testDebugUnitTest ktlintCheck detekt -q
fi
echo "▸ building signed release APK…"
./gradlew :app:assembleRelease -q
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "✗ APK not found at $APK"; exit 1; }
echo "  $(du -h "$APK" | cut -f1)  $APK"

# --- upload to R2 via the Worker ----------------------------------------------------------------------
echo "▸ uploading APK to $BASE/v1/apk …"
UP=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT \
  "$BASE/v1/admin/apk?versionCode=$NEW_CODE&versionName=$NEW_NAME" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK")
if [ "$UP" = "503" ]; then
  echo "✗ upload got 503 — no R2 bucket bound. Enable R2 + uncomment [[r2_buckets]] in wrangler.toml, redeploy."
  echo "  (Or host the APK yourself and set updateUrl manually.)"; exit 1
fi
[ "$UP" = "200" ] || { echo "✗ upload failed (HTTP $UP)"; exit 1; }

# --- advertise the new build --------------------------------------------------------------------------
echo "▸ advertising latest build (force=$FORCE) …"
PATCH="{\"updateUrl\":\"$BASE/v1/apk\",\"latestVersionCode\":$NEW_CODE,\"latestVersionName\":\"$NEW_NAME\""
[ "$FORCE" = 1 ] && PATCH="$PATCH,\"minSupportedVersionCode\":$NEW_CODE"
PATCH="$PATCH}"
CFG=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT "$BASE/v1/config" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "$PATCH")
[ "$CFG" = "200" ] || { echo "✗ config update failed (HTTP $CFG)"; exit 1; }

echo "✓ released $NEW_NAME ($NEW_CODE) — live at $BASE/v1/apk"
[ "$FORCE" = 1 ] && echo "  forced: builds below $NEW_CODE are now blocked until they update."
echo "  Tip: commit version.properties so the bump is recorded."
