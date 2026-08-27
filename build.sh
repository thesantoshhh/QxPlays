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
  echo "🔧 GitHub Actions environment detected"
  echo "📁 ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
  
  AAPT2="$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2"
  ZIPALIGN="$ANDROID_SDK_ROOT/build-tools/35.0.0/zipalign"
  ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
  JDK_BIN="/usr/bin"
  R8JAR="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/r8.jar"
  APKSIGNER_JAR="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/apksigner.jar"
  LIB=""
  
  # Verify critical tools exist
  echo "✓ Verifying tools..."
  [ -f "$AAPT2" ] && echo "  ✓ aapt2 found" || (echo "  ✗ aapt2 NOT found"; exit 1)
  [ -f "$ANDROID_JAR" ] && echo "  ✓ android.jar found" || (echo "  ✗ android.jar NOT found"; exit 1)
  [ -f "$ZIPALIGN" ] && echo "  ✓ zipalign found" || (echo "  ✗ zipalign NOT found"; exit 1)
  
  # Check for r8.jar, download if needed
  if [ ! -f "$R8JAR" ]; then
    echo "  ⚠ r8.jar not found, attempting download..."
    mkdir -p "$ANDROID_SDK_ROOT/build-tools/35.0.0/lib"
    curl -L "https://repo1.maven.org/maven2/com/android/tools/r8/8.2.47/r8-8.2.47.jar" \
      -o "$R8JAR" 2>/dev/null || {
      echo "  ✗ Failed to download r8.jar"
      exit 1
    }
    echo "  ✓ r8.jar downloaded"
  else
    echo "  ✓ r8.jar found"
  fi
  
else
  # Local development environment
  echo "💻 Local development environment"
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

echo ""
echo "🗑️  Cleaning previous builds..."
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" release

echo ""
echo "📦 Building APK (7 steps)..."
echo ""

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
CLASSES_COUNT=$(find "$OUT/classes" -name '*.class' | wc -l)
echo "     ✓ compiled $CLASSES_COUNT classes"

echo "[4/7] d8 (dex)"
(cd "$OUT/classes" && "$JDK_BIN/jar" cf ../app.jar .)

if [ -f "$R8JAR" ]; then
  "$JDK_BIN/java" -Xshare:off -cp "$R8JAR" com.android.tools.r8.D8 \
    --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app.jar"
else
  echo "     ✗ r8.jar not found at: $R8JAR"
  exit 1
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
echo "✅ ================ VERIFY ================"
"$JDK_BIN/java" -Xshare:off -jar "$APKSIGNER_JAR" verify --print-certs "$APK_OUT" | sed -n '1,6p'
echo ""
echo "📋 APK Details:"
"$AAPT2" dump badging "$APK_OUT" | grep -E "package:|application-label:|sdkVersion|targetSdkVersion|uses-permission" | head -24
echo ""
echo "🎉 ================ DONE ================"
ls -lh "$APK_OUT"
echo ""
echo "✨ APK successfully built: $APK_OUT"
