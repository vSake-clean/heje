#!/usr/bin/env bash
# Buduje EkranTelefonu.apk bez Gradle (wymaga Android SDK: platforms;android-34, build-tools;35.0.0 i JDK).
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/build"
DIST="$HERE/../dist"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex" "$DIST"

"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$HERE/AndroidManifest.xml" \
    -o "$OUT/unsigned.apk" "$OUT/res.zip" --java "$OUT/gen"

javac --release 8 -nowarn -encoding UTF-8 -classpath "$JAR" -d "$OUT/classes" \
    $(find "$HERE/src" "$OUT/gen" -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true
"$BT/d8" --release --min-api 21 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$HERE/debug.keystore"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias ekran \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=EkranTelefonu" >/dev/null 2>&1
fi
"$BT/apksigner" sign --v4-signing-enabled false --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$DIST/EkranTelefonu.apk" "$OUT/aligned.apk"
echo "Gotowe: $DIST/EkranTelefonu.apk"
