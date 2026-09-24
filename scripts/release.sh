#!/usr/bin/env bash
# Build, upload, and advertise a release. With no version argument, bump the patch version.
# Usage: scripts/release.sh [VERSION] [--code=N] [--force] [--check] [--prepare]
# --force blocks older builds. --check runs tests and lint. --prepare stops before upload.
# Publish a prepared APK: scripts/release.sh --publish-prepared --sha256=HASH [--force]
# Requires REMOTE_CONFIG_URL, ADMIN_TOKEN, and RELEASE_* signing keys in local.properties, plus Worker R2.

set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE="./gradlew"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) GRADLE="./gradlew.bat" ;;
esac

prop() { grep -E "^$1=" local.properties 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }

BASE="$(prop REMOTE_CONFIG_URL)"; BASE="${BASE%/}"
ADMIN_TOKEN="$(prop ADMIN_TOKEN)"
[ -n "$BASE" ]        || { echo "✗ REMOTE_CONFIG_URL missing from local.properties"; exit 1; }
[ -n "$ADMIN_TOKEN" ] || { echo "✗ ADMIN_TOKEN missing from local.properties (wrangler secret value)"; exit 1; }

FORCE=0; CHECK=0; PREPARE=0; PUBLISH_PREPARED=0; EXPECTED_SHA256=""; NEW_NAME=""; NEW_CODE=""
for a in "$@"; do
  case "$a" in
    --force) FORCE=1 ;;
    --check) CHECK=1 ;;
    --prepare) PREPARE=1 ;;
    --publish-prepared) PUBLISH_PREPARED=1 ;;
    --sha256=*) EXPECTED_SHA256="${a#*=}" ;;
    --code=*) NEW_CODE="${a#*=}" ;;
    -*)      echo "unknown flag: $a"; exit 1 ;;
    *)       NEW_NAME="$a" ;;
  esac
done
[ "$PREPARE" = 0 ] || [ "$PUBLISH_PREPARED" = 0 ] || { echo "--prepare and --publish-prepared cannot be combined"; exit 1; }
if [ "$PUBLISH_PREPARED" = 1 ]; then
  [ -z "$NEW_NAME" ] && [ -z "$NEW_CODE" ] && [ "$CHECK" = 0 ] || {
    echo "--publish-prepared uses the current version and APK"; exit 1;
  }
  [ -n "$EXPECTED_SHA256" ] || { echo "--publish-prepared requires --sha256"; exit 1; }
elif [ -n "$EXPECTED_SHA256" ]; then
  echo "--sha256 requires --publish-prepared"; exit 1
fi

OLD_CODE="$(grep -E '^VERSION_CODE=' version.properties | cut -d= -f2- | tr -d '\r')"
OLD_NAME="$(grep -E '^VERSION_NAME=' version.properties | cut -d= -f2- | tr -d '\r')"
if [ "$PUBLISH_PREPARED" = 1 ]; then
  NEW_CODE="$OLD_CODE"
  NEW_NAME="$OLD_NAME"
else
  [ -n "$NEW_CODE" ] || NEW_CODE=$(( OLD_CODE + 1 ))
  case "$NEW_CODE" in
    *[!0-9]*|'') echo "version code must be a positive integer"; exit 1 ;;
  esac
  [ "$NEW_CODE" -gt "$OLD_CODE" ] || { echo "version code must be greater than $OLD_CODE"; exit 1; }
  if [ -z "$NEW_NAME" ]; then
    IFS=. read -r MA MI PA <<<"$OLD_NAME"
    NEW_NAME="${MA:-1}.${MI:-0}.$(( ${PA:-0} + 1 ))"
  fi
  printf 'VERSION_CODE=%s\nVERSION_NAME=%s\n' "$NEW_CODE" "$NEW_NAME" > version.properties
  echo "▸ version  $OLD_NAME ($OLD_CODE) → $NEW_NAME ($NEW_CODE)"
fi

if [ "$PUBLISH_PREPARED" = 0 ]; then
  if [ "$CHECK" = 1 ]; then
    echo "▸ running test + lint gate…"
    "$GRADLE" :app:testDebugUnitTest ktlintCheck detekt -q
  fi
  echo "▸ building signed release APK…"
  "$GRADLE" :app:assembleRelease -q
fi
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "✗ APK not found at $APK"; exit 1; }
echo "  $(du -h "$APK" | cut -f1)  $APK"
if [ "$PUBLISH_PREPARED" = 1 ]; then
  ACTUAL_SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
  [ "${ACTUAL_SHA256,,}" = "${EXPECTED_SHA256,,}" ] || { echo "✗ prepared APK hash does not match"; exit 1; }
fi

if [ "$PREPARE" = 1 ]; then
  echo "✓ prepared $NEW_NAME ($NEW_CODE) — $APK"
  exit 0
fi

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
