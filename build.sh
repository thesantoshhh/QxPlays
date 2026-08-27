#!/bin/bash
# ============================================================
#  QxPlays APK builder — pure AOSP toolchain, no Gradle, no network
#  Pipeline: aapt2 compile/link -> javac -> d8 (dex) -> zip -> zipalign -> apksigner
# ============================================================
set -e
cd "$(dirname "$0")"

# Check if running in GitHub Actions environment
if [ -n "$ANDROID_SDK_ROOT" ]; then
  # GitHub Actions environment
  AAPT2="$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2"
  ZIPALIGN="$ANDROID_SDK_ROOT/build-tools/35.0.0/zipalign"
  ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
  # Use system JDK
  JDK_BIN="/usr/bin"
  # R8/D8 is included in build-tools, use it directly
  R8JAR="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/r8.jar"
  APKSIGNER_JAR="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/apksigner.jar"
  LIB=""
else
  # Local development environment
  TC=/home/user/toolchain
  JDK_BIN=$TC/jdk/bin
  ANDROID_JAR=$TC/sdk/platforms/an35/android.jar
  AAPT2=$TC/aapt2-npm
  ZIPALIGN=$TC/zipalign
  R8JAR=$TC/r8.jar
  APKSIGNER_JAR=$TC/apksigner.jar
  LIB=$TC/libc++.so
fi

APP=app/src/main
OUT=build
APK_OUT=release/QxPlays-v1.0.0.apk

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" release

echo "[1/7] aapt2 compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$OUT/res.zip" --java "$OUT/gen" --auto-add-overlay \
  --min-sdk-version 24 --target-sdk-version 35

echo "[3/7] javac"
find "$APP/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
"$JDK_BIN/javac" --release 8 -nowarn -encoding UTF-8 \
  -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
echo "     compiled $(find "$OUT/classes" -name '*.class' | wc -l) classes"

echo "[4/7] d8 (dex)"
(cd "$OUT/classes" && "$JDK_BIN/jar" cf ../app.jar .)

# Try to find r8.jar in multiple locations
if [ -f "$R8JAR" ]; then
  R8_PATH="$R8JAR"
elif [ -f "$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/r8.jar" ]; then
  R8_PATH="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/r8.jar"
elif command -v d8 &> /dev/null; then
  # Use d8 command if available in PATH
  echo "Using d8 from PATH"
  d8 --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app.jar"
  R8_PATH="SKIP"
else
  echo "ERROR: Could not find r8.jar or d8 command"
  exit 1
fi

if [ "$R8_PATH" != "SKIP" ]; then
  "$JDK_BIN/java" -Xshare:off -cp "$R8_PATH" com.android.tools.r8.D8 \
    --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app.jar"
fi

echo "[5/7] package dex into APK"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python3 - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED)
z.write(dex, "classes.dex")
z.close()
PY

echo "[6/7] zipalign"
if [ -n "$LIB" ]; then
  env LD_LIBRARY_PATH="$TC" "$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
else
  "$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
fi

echo "[7/7] sign (apksigner, v1+v2)"
"$JDK_BIN/java" -Xshare:off -jar "$APKSIGNER_JAR" sign \
  --ks keystore/qxplays-release.keystore --ks-key-alias qxplays \
  --ks-pass pass:QxPlays2026 --key-pass pass:QxPlays2026 \
  --out "$APK_OUT" "$OUT/aligned.apk"

echo ""
echo "================ VERIFY ================"
"$JDK_BIN/java" -Xshare:off -jar "$APKSIGNER_JAR" verify --print-certs "$APK_OUT" | sed -n '1,6p'
echo ""
"$AAPT2" dump badging "$APK_OUT" | grep -E "package:|application-label:|sdkVersion|targetSdkVersion|uses-permission" | head -24
echo ""
echo "================ DONE ================"
ls -la "$APK_OUT"
