#!/usr/bin/env bash
# Downloads the stock app at build time, applies open-source patches, signs with the keystore supplied via env.
# Env: PATCHES_TAG (default: latest release incl. pre-releases), APP_VERSION, KS_FILE, KS_PASS, KS_ALIAS
set -euo pipefail
APP_VERSION="${APP_VERSION:-21.13.164}"
PATCHES_TAG="${PATCHES_TAG:-latest}"
KS_ALIAS="${KS_ALIAS:-pmb}"
OUT=out; WORK=work
mkdir -p "$OUT" "$WORK"

gh_api() { curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} "$@"; }

# 1. Patch bundle
if [ "$PATCHES_TAG" = latest ]; then
  PATCHES_TAG=$(gh_api "https://api.github.com/repos/anddea/revanced-patches/releases?per_page=1" | jq -r '.[0].tag_name')
fi
echo "Patches: $PATCHES_TAG"
PURL=$(gh_api "https://api.github.com/repos/anddea/revanced-patches/releases/tags/$PATCHES_TAG" | jq -r '.assets[]|select(.name|endswith(".mpp"))|.browser_download_url')
curl -fsSL -o "$WORK/patches.mpp" "$PURL"

# 2. ReVanced CLI
CLI_TAG=v6.0.0
curl -fsSL -o "$WORK/cli.jar" "https://github.com/ReVanced/revanced-cli/releases/download/$CLI_TAG/revanced-cli-6.0.0-all.jar"

# 3. Stock APK (community archive.org mirror of the unmodified Google APK), verified against Google's signing cert below
STOCK="$WORK/youtube-$APP_VERSION.apk"
curl -fL --retry 3 -o "$STOCK" "https://archive.org/download/jhc-apks/apks/com.google.android.youtube/com.google.android.youtube-$APP_VERSION-all.apk"
APKSIGNER=$(ls -d "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
if [ -n "$APKSIGNER" ]; then
  "$APKSIGNER" verify --print-certs "$STOCK" > "$WORK/stock-cert.txt" || true; head -3 "$WORK/stock-cert.txt"
  echo "Stock APK signer (compare with Google release cert):"; grep -i "SHA-256" "$WORK/stock-cert.txt" | head -1 || true
fi

# 4. microG (GmsCore) needed for Google login with the patched app
GMS_URL=$(gh_api https://api.github.com/repos/ReVanced/GmsCore/releases/latest | jq -r '.assets[]|select(.name|test("-signed.apk$") and (test("hw")|not))|.browser_download_url' | head -1)
curl -fsSL -o "$OUT/microG-GmsCore.apk" "$GMS_URL"

# 5. Patch + sign (default-enabled patches of the bundle: ads, SponsorBlock, background playback, GmsCore support, ...)
java -jar "$WORK/cli.jar" patch -b -p "$WORK/patches.mpp" \
  --keystore="$KS_FILE" --keystore-password="$KS_PASS" \
  --keystore-entry-alias="$KS_ALIAS" --keystore-entry-password="$KS_PASS" \
  -o "$OUT/patched-$APP_VERSION.apk" "$STOCK" 2>&1 | tee "$WORK/patch.log" | tail -40
echo "$PATCHES_TAG" > "$OUT/patches-version.txt"
ls -la "$OUT"
