#!/usr/bin/env bash
# Builds the anddea patch bundle (.mpp) from source at PATCHES_TAG, applying our local
# extension overrides from patches-custom/ (in-app microG install prompt, stock header).
# Every *.java file in patches-custom/ replaces the file of the same name in
# extensions/shared/src/main/java/app/morphe/extension/shared/patches/.
# Env: PATCHES_TAG (e.g. v4.3.0); GITHUB_TOKEN + GITHUB_ACTOR (MorpheApp Gradle plugin
# registry, same as anddea's own CI); JAVA_HOME_17_X64 (setup-java; anddea builds with 17).
set -euo pipefail
TAG="${PATCHES_TAG:?PATCHES_TAG is required}"
SRC=work/patches-src
rm -rf "$SRC"
git clone --depth 1 --branch "$TAG" https://github.com/anddea/revanced-patches "$SRC"
for OVERRIDE in patches-custom/*.java; do
  NAME=$(basename "$OVERRIDE")
  DEST="$SRC/extensions/shared/src/main/java/app/morphe/extension/shared/patches/$NAME"
  if [ -f "$DEST" ]; then
    echo "Applied override: $NAME"
  else
    echo "Added new extension file: $NAME"
  fi
  cp "$OVERRIDE" "$DEST"
done
# Kotlin patch overrides: files under patches-custom/kotlin/ mirror paths under
# patches/src/main/kotlin/ (e.g. app/morphe/patches/shared/gms/GmsCoreSupportPatch.kt).
if [ -d patches-custom/kotlin ]; then
  (cd patches-custom/kotlin && find . -type f) | while read -r KFILE; do
    DEST="$SRC/patches/src/main/kotlin/${KFILE#./}"
    if [ -f "$DEST" ]; then
      echo "Applied Kotlin override: ${KFILE#./}"
    else
      echo "Added new Kotlin file: ${KFILE#./}"
    fi
    mkdir -p "$(dirname "$DEST")"
    cp "patches-custom/kotlin/${KFILE#./}" "$DEST"
  done
fi
# Resource overrides: files under patches-custom/resources/ mirror paths under
# patches/src/main/resources/ (e.g. youtube/settings/xml/revanced_prefs.xml).
if [ -d patches-custom/resources ]; then
  (cd patches-custom/resources && find . -type f) | while read -r RES; do
    DEST="$SRC/patches/src/main/resources/${RES#./}"
    if [ -f "$DEST" ]; then
      echo "Applied resource override: ${RES#./}"
    else
      echo "Added new resource file: ${RES#./}"
    fi
    mkdir -p "$(dirname "$DEST")"
    cp "patches-custom/resources/${RES#./}" "$DEST"
  done
fi
cd "$SRC"
chmod +x gradlew
export JAVA_HOME="${JAVA_HOME_17_X64:?setup-java with java-version 17 is required}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :patches:buildAndroid --no-daemon
cd - >/dev/null
MPP=$(find "$SRC" -name '*.mpp' | head -1)
test -n "$MPP"
cp "$MPP" work/patches.mpp
echo "$TAG+custom-overrides" > work/bundle-version.txt
echo "Custom bundle built from $TAG: $MPP"
