# Digix

Dial a short code on your watch and your Android phone runs an action, with no call placed and no watch app. Any Bluetooth watch that can place calls through the phone can use it. Only the OnePlus Watch 3 has been tested so far.

## Install the test build

`dist/Digix-1.1-test.apk` is shrunk like a release build, but debuggable and signed with a debug key. Don't distribute it. Copy it to the phone and open it from a file manager.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleStaging
```

The APK is written to `app/build/outputs/apk/staging/`. A public release needs your own signing key.

## Test

```bash
scripts/emulator_tests.sh emulator-5554
```

Set `MOCK_LOG` and `MOCK_PORT`, and run `scripts/mock_assistant.py` on that port, to include the voice assistant tests. The suite only runs against an emulator, whose modem is simulated, so no real call can leave the machine. Run it with the staging build installed. Don't inspect the screen with `uiautomator` while testing the helper, because it switches accessibility services off while it reads the screen.

## How it works

- `engine/CallInterceptor` sees each outgoing number before it is placed. Codes are stopped, and every other number passes through untouched and unsaved.
- `engine/ReplyCallService` answers a code with a short private call that stays on the phone. The watch connects at once, hears the result through `engine/Speaker`, and the call hangs up. On a phone already in a call, no reply call starts and the action runs silently.
- `engine/ActionService` runs the action and reports the result. It only starts when a code arrives.
- Wi-Fi switches directly, which Android still allows at this app's target. Hotspot uses Shizuku if connected, then "Modify system settings" on Android 15 and below, then the accessibility helper.
- `engine/SosService` runs an SOS: an instant text, a live position, then updates. `engine/SmsSender` reports what the network really said, and `engine/LocationTracker` follows location.
- `engine/VoiceListener`, `engine/AssistantClient` and the reply call form the voice assistant. It listens for a pause, sends that turn to the chosen provider, speaks the reply, and can run the person's other codes.

## Google Home

The Google Home action is built only when Google's library is on this machine. It's in open beta, isn't on Google's public Maven, and needs a Google sign-in to download.

1. Sign in at [the Home APIs SDK page](https://developers.home.google.com/apis/android/sdk), download the library, and put it in `~/.m2/repository`, so `com/google/android/gms/play-services-home-types` exists. The build finds it and includes the action.
2. In [Google Cloud](https://console.cloud.google.com), set up the OAuth consent screen and add each person's Google account as a test user. Then create an Android OAuth client for package `com.littyl.digix` with the SHA-1 of the signing key, which `keytool -list -v -keystore ~/.android/debug.keystore -storepass android` prints for the test build. The current test build uses the Google Cloud project `dialect-513905`, where you add new test users under Google Auth Platform → Audience.

Library 17.1.0 is built with Kotlin 2.4, so the project needs Kotlin 2.4 or later.

Until Google opens its Developer Console, only those test users can connect, with a limit of 100. Code that works without the library lives in `app/src/main`, and each build adds one folder: `home-sdk` for the real library, `home-off` to leave the action out, or `home-fake` for a pretend home. Build with `-Pdigix.home=fake` and set `HOME_FAKE=1` to include the Google Home tests, because emulators can't run Google's library.

See `PRODUCT.md` for product decisions and `DESIGN.md` for the design system.
