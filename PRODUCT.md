# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated: Kotlin + Jetpack Compose. Chosen because the product depends on Android Telecom APIs, spring motion, haptics and accessibility, and has no iOS equivalent.

## Users

People worldwide who own a Bluetooth watch that can place calls through their Android phone but cannot run custom apps on the watch, or who keep it in a low-power mode where apps are unavailable. Their hands or phone are occupied or out of reach, and they want a phone action to happen from the wrist without unlocking the phone. They are comfortable with phone settings, but not necessarily developers, and most will not have ADB or Shizuku.

## Product Purpose

Digix turns a watch's dial pad into a remote control for the phone. Dial a short code on the watch and the phone runs an action instead of placing a call. Success means a person can dial a code and trust the result, every time, without looking at the phone.

## Positioning

No watch app and no watch compatibility list. The mechanism is the plain phone call every Bluetooth watch already knows how to make: Digix catches the number before it leaves the phone, so no call is placed.

## Operating Context

- Setup happens on the phone, usually once, in one sitting.
- Daily use happens on the watch dial pad, with the phone locked or in a pocket. The app is opened rarely: to change a code, check a run, or switch Digix off.
- Some watches do not sync favorites or contacts in low-power modes, so codes are typed and must stay short and memorable.
- The phone may be dual SIM, in any country, on any carrier.

## Capabilities and Constraints

- Android only. Outgoing calls are caught with `CallRedirectionService` through the call redirection role.
- Proven on Samsung S26 Ultra (Android 17) with OnePlus Watch 3 in Power Saver: watch dials arrive as standard Bluetooth Handsfree calls, are cancelled before dialing, and wake the app from a stopped state.
- A dialed code is answered with a short private call (self-managed, no phone network, no call log): the watch connects at once, hears the result, and the call hangs up. This is on by default and can be switched off in Settings.
- A real call always wins. A reply call never starts while a call is active or ringing, and it ends at once if a real call is dialed.
- SOS: dialing its code silently texts emergency contacts at once with the last known location, follows with a live position, updates every 5 minutes for an hour, and stops when dialed again. It runs even if the master switch is off, retries across SIMs, and resumes after the app is killed or the phone restarts and is unlocked. It needs mobile signal and doesn't contact emergency services, and the app must say so plainly.
- Other actions: speed dial (the dial becomes a real call), send a text, share location by text, and WhatsApp (needs the helper and an unlocked phone, and is not suitable for emergencies).
- Google Home: switches one light, plug, fan or air conditioner on, off, or to the opposite state, and speaks the real result. It's limited to 100 test users until Google opens its Developer Console, so public builds leave it out.
- A master on/off switch controls whether actions run. While Digix holds the role and is switched off, its codes must still be stopped rather than dialed.
- Normal numbers must always pass through unchanged, including when the app fails.
- Emergency numbers are never intercepted, and a code can never be an emergency number or start with one in any country.
- Media controls and Find my phone run after the reply call ends, because Android sends media keys to the call while one is up.
- Wi-Fi needs no setup. Hotspot uses "Modify system settings" on Android 15 and below. Android 16 refuses that route, so there it needs the Accessibility helper or Shizuku.
- ADB or Shizuku is never required.
- Distribution: direct APK first, Google Play later. Onboarding must handle Android's restricted settings step for sideloaded installs.
- The voice assistant is an action bound to a code. People bring their own key from Gemini, Groq, or any OpenAI-style service, and the audio goes only to the provider they choose. The Claude API is deferred.
- The direct APK deliberately targets Android 9 (API 28), which keeps direct Wi-Fi switching, the hotspot route on Android 15 and below, and background starts. Google Play won't accept this target, so a Play release needs its own build.
- Undecided: final name (Digix is a working name), pricing, developer identity for Android developer verification.

## Brand Commitments

The user first pinned an Apple-style look in the manner of shadcn, then, after exploring directions on a design canvas, chose **Halo + Quiet**: black, a dot-matrix halo behind each screen's one big thing, plain readable lists with monospace codes, red only for what is live, and the "digix" wordmark (see DESIGN.md). No themed concepts or skeuomorphism. They also require a beautiful onboarding experience, a clear enable and disable toggle, and animations with clear intent.

## Evidence on Hand

Real device test logs from this project only. Only the OnePlus Watch 3 and Samsung S26 Ultra pair has been tested. There are no users, testimonials, benchmarks or compatibility lists, and none may be invented.

## Product Principles

1. Trust beats features. A code either does what it says or says plainly why it did not.
2. Never place an unintended call. Every failure path ends in a stopped code or an untouched normal call.
3. The watch is the product. The phone app exists to set it up and get out of the way.
4. Earn each permission. Ask only when the person has chosen something that needs it, and say why in plain words.
5. Be honest about what a phone allows. Show which actions will work on this phone before someone relies on them.

## Accessibility & Inclusion

Global audience: all copy externalized for translation, layouts support right-to-left languages and large font scales, numerals stay legible, and motion respects the system's remove-animations setting. Every state is conveyed by more than color. TalkBack must be able to complete onboarding and operate the master switch.
