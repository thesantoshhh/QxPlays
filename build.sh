#!/bin/bash
# ============================================================
#  QxPlays APK builder — pure AOSP toolchain, no Gradle, no network
#  Pipeline: aapt2 compile/link -> javac -> d8 (dex) -> zip -> zipalign -> apksigner
# ============================================================
set -e
cd "$(dirname "$0")"

TC=/home/user/toolchain
JDK_BIN=$TC/jdk/bin
ANDROID_JAR=$TC/sdk/platforms/an35/android.jar
AAPT2=$TC/aapt2-npm
ZIPALIGN=$TC/zipalign
R8JAR=$TC/r8.jar
APKSIGNER_JAR=$TC/apksigner.jar
LIB=$TC/libc++.so

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
"$JDK_BIN/java" -Xshare:off -cp "$R8JAR" com.android.tools.r8.D8 \
  --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app.jar"

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
env LD_LIBRARY_PATH="$TC" "$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

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
