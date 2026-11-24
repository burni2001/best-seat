#!/bin/bash
# Baut Best Seat (eigener Launcher, Paket tv.bestseat.launcher) ohne Gradle direkt mit den Android Build-Tools.
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

"$BT/aapt2" link --manifest "$DIR/AndroidManifest.xml" -I "$ANDROID_JAR" "$OUT/res.zip" -A "$DIR/assets" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 134 --version-name 0.22.1 \
  -o "$OUT/unsigned.apk"

# Paketnamen der eigenen Begleit-Apps (Hue Sync, Helper, VPN-Status ...) stehen nur lokal in
# companions.properties (nicht im Repository). Fehlt die Datei, gelten die Platzhalter aus
# companions.example.properties: die zugehörigen Kacheln tun dann nichts.
COMP="$DIR/companions.properties"
[ -f "$COMP" ] || COMP="$DIR/companions.example.properties"
mkdir -p "$OUT/gen/tv/bestseat/launcher"
{
  echo "package tv.bestseat.launcher;"
  echo "/** Erzeugt von build.sh aus $(basename "$COMP"). */"
  echo "public final class Companions {"
  echo "    private Companions() {}"
  grep -E '^[A-Z_]+=[a-zA-Z0-9_.]+$' "$COMP" | while IFS='=' read -r k v; do
    echo "    public static final String $k = \"$v\";"
  done
  echo "}"
} > "$OUT/gen/tv/bestseat/launcher/Companions.java"

find "$DIR/src" "$OUT/gen" -name '*.java' -exec \
  javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -Xlint:-options \
  -d "$OUT/classes" {} +

find "$OUT/classes" -name '*.class' -exec \
  "$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dex" {} +

(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/launcher.apk" "$OUT/aligned.apk"

echo "Fertig: $OUT/launcher.apk"
