#!/bin/bash
# Baut Best Seat (eigener Launcher, Paket tv.bernhard.launcher) ohne Gradle direkt mit den Android Build-Tools.
# Ergebnis: build/launcher.apk
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
SDK=/opt/homebrew/share/android-commandlinetools
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
OUT="$DIR/build"
KEYSTORE="$DIR/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

# Signaturschlüssel einmalig anlegen und danach immer wiederverwenden,
# sonst lässt sich die App nicht über eine ältere Version installieren.
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias launcher -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Best Seat" >/dev/null
fi

# Banner (TV-Kachel) und App-Symbol aus res/ übersetzen.
"$BT/aapt2" compile --dir "$DIR/res" -o "$OUT/res.zip"

"$BT/aapt2" link --manifest "$DIR/AndroidManifest.xml" -I "$ANDROID_JAR" "$OUT/res.zip" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 105 --version-name 0.18.13 \
  -o "$OUT/unsigned.apk"

find "$DIR/src" -name '*.java' -exec \
  javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -Xlint:-options \
  -d "$OUT/classes" {} +

find "$OUT/classes" -name '*.class' -exec \
  "$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dex" {} +

(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/launcher.apk" "$OUT/aligned.apk"

echo "Fertig: $OUT/launcher.apk"
