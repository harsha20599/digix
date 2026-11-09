#!/bin/bash
# Builds the probe APK with the Android SDK tools, without Gradle.
set -euo pipefail
cd "$(dirname "$0")"

SDK="$HOME/Library/Android/sdk"
TOOLS="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf build && mkdir -p build/classes build/dex

"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 31 --target-sdk-version 34 -o build/base.apk

javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -Xlint:-options \
  -d build/classes src/dev/watchtrigger/probe/*.java

"$TOOLS/d8" --release --min-api 31 --lib "$ANDROID_JAR" --output build/dex \
  $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

[ -f probe.keystore ] || keytool -genkeypair -keystore probe.keystore -storepass probepass \
  -keypass probepass -alias probe -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=Watch Trigger Probe" 2>/dev/null

"$TOOLS/apksigner" sign --ks probe.keystore --ks-pass pass:probepass \
  --out build/probe.apk build/aligned.apk
"$TOOLS/apksigner" verify build/probe.apk
echo "built build/probe.apk"
