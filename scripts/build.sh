#!/usr/bin/env bash
# Downloads the stock app at build time, applies open-source patches, signs with the keystore supplied via env.
# Env: PATCHES_TAG (default: latest release incl. pre-releases), APP_VERSION, KS_FILE, KS_PASS, KS_ALIAS
set -euo pipefail
APP_VERSION="${APP_VERSION:-21.13.164}"
PACKAGE_NAME="${PACKAGE_NAME:-anddea.youtube}"
APP_LABEL="${APP_LABEL:-YouTube}"
PATCHES_TAG="${PATCHES_TAG:-latest}"
KS_ALIAS="${KS_ALIAS:-pmb}"
OUT=out; WORK=work
mkdir -p "$OUT" "$WORK"

gh_api() { curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} "$@"; }

# 1. Patch bundle (a prebuilt bundle from scripts/build-patches.sh wins if present)
if [ -s "$WORK/patches.mpp" ]; then
  echo "Patches: using prebuilt custom bundle at $WORK/patches.mpp"
else
  if [ "$PATCHES_TAG" = latest ]; then
    PATCHES_TAG=$(gh_api "https://api.github.com/repos/anddea/revanced-patches/releases?per_page=1" | jq -r '.[0].tag_name')
  fi
  echo "Patches: $PATCHES_TAG"
  PURL=$(gh_api "https://api.github.com/repos/anddea/revanced-patches/releases/tags/$PATCHES_TAG" | jq -r '.assets[]|select(.name|endswith(".mpp"))|.browser_download_url')
  curl -fsSL -o "$WORK/patches.mpp" "$PURL"
fi

# 2. Patcher CLI (the anddea bundle is built for the Morphe patcher engine, needs Java 21)
CLI_TAG=v1.19.0
[ -s "$WORK/cli.jar" ] || curl -fsSL -o "$WORK/cli.jar" "https://github.com/MorpheApp/morphe-desktop/releases/download/$CLI_TAG/morphe-desktop-1.19.0-all.jar"

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
pip install --quiet pillow >/dev/null 2>&1 || true
python3 scripts/make_icons.py "$WORK/icons"
java -jar "$WORK/cli.jar" patch -p "$WORK/patches.mpp" \
  -e "Custom branding for YouTube" -O "customName=$APP_LABEL" -O appIcon=custom -O "customIcon=$PWD/$WORK/icons" \
  -e "GmsCore support" -O "packageNameYouTube=$PACKAGE_NAME" \
  --keystore="$KS_FILE" --keystore-password="$KS_PASS" \
  --keystore-entry-alias="$KS_ALIAS" --keystore-entry-password="$KS_PASS" \
  -o "$OUT/patched-$APP_VERSION.apk" "$STOCK" 2>&1 | tee "$WORK/patch.log" | tail -60
if grep -qE 'SEVERE|NoClassDefFound|Failed to load patches' "$WORK/patch.log"; then
  echo "::error::Patching reported errors"; exit 1
fi
test -s "$OUT/patched-$APP_VERSION.apk"
{ cat "$WORK/bundle-version.txt" 2>/dev/null || echo "$PATCHES_TAG"; } > "$OUT/patches-version.txt"
ls -la "$OUT"
