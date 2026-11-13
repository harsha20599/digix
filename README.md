# Digix

Dial a short code on your watch and your Android phone runs an action, with no call placed and no watch app. Any Bluetooth watch that can place calls through the phone can use it. Only the OnePlus Watch 3 has been tested so far.

## Editions

Both editions share DigiX’s interface and action engine. The sideload app retains its original package and legacy Android target. The Play build keeps the published package, targets Android 16, and uses Android’s current permission and setup flows.

## Build

With the Android SDK and a compatible JDK configured:

```bash
./gradlew :app:assembleStaging
./gradlew -Pdigix.play=true :app:assembleStaging :app:bundleRelease
```

The staging APK uses a debug key for testing. Sign a public sideload APK with the distribution key. Sign the Play release bundle with the app’s upload key before submitting it. Build the editions separately because their artifacts use the same output names.

## Test

Install the sideload staging APK on a disposable emulator and run:

```bash
zsh scripts/emulator_tests.sh emulator-5554
```

This suite clears DigiX data and sends simulated calls and texts. It refuses to run on a physical device. Set `MOCK_LOG` and `MOCK_PORT` and run `scripts/mock_assistant.py` to include the voice assistant checks. Avoid running UI Automator during the sideload tests because it temporarily disconnects accessibility services.

## How it works

- `engine/CallInterceptor` sees each outgoing number before it is placed. Codes are stopped, and every other number passes through untouched and unsaved.
- `engine/ReplyCallService` answers a code with a short private call that stays on the phone. The watch connects at once, hears the result through `engine/Speaker`, and the call hangs up. On a phone already in a call, no reply call starts and the action runs silently.
- `engine/ActionService` runs the action and reports the result. It only starts when a code arrives.
- Wi-Fi switches directly, which Android still allows at this app's target. Hotspot uses Shizuku if connected, then "Modify system settings" on Android 15 and below, then the accessibility helper.
- `engine/SosService` runs an SOS: an instant text, a live position, then updates. `engine/SmsSender` reports what the network really said, and `engine/LocationTracker` follows location.
- `engine/VoiceListener`, `engine/AssistantClient` and the reply call form the voice assistant. It listens for a pause, sends that turn to the chosen provider, speaks the reply, and can run the person's other codes.

## Google Home

Google Home support is coming later, pending verification. The sideload build excludes the SDK by default, even when it is installed locally. Maintainers can use `-Pdigix.home=fake` and `HOME_FAKE=1` to test the development integration. The real integration requires the local SDK and a configured test account and is enabled only with `-Pdigix.home=sdk`.

See `PRODUCT.md` for product decisions and `DESIGN.md` for the design system.
