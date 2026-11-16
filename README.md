<p align="center"><img src="assets/kwata-logo.png" width="120" alt="Kwata logo"></p>

# Kwata Tracker

Family safety and live location sharing, built by **Kwata Enterprise**, a defence technology company under **Motswana Intelligence**. Kwata builds secure software and hardware to protect people, families and nations.

## Features
- Full-screen live map with your circle's locations
- SOS with countdown, cancelled only with your PIN
- Buzz a member of your circle
- Consent-based circle invites (QR code or link)
- Real-time sync via Supabase (row-level security on every table)
- HD maps: CARTO retina street map and Esri satellite hybrid with roads and labels
- Live weather for the area on screen (Open-Meteo), animated rain radar (RainViewer), and rain, snow, storm, fog and cloud effects simulated on the map from real conditions

## Try it
- **Web app:** https://kwatawarfare-sketch.github.io/kwata-tracker/
- **Android:** download the APK from the [latest release](../../releases/latest) (or [`releases/KwataTracker.apk`](releases/KwataTracker.apk)), allow installs from unknown sources, then grant location access. (Debug-signed build for sideloading.)

## Project layout
| Path | What it is |
|---|---|
| `index.html`, `hark.js`, `hark.css`, `hark-hero.css` | The web app (served by GitHub Pages) |
| `android-app/` | Capacitor 6 Android project (`com.kwata.tracker`, minSdk 22, targetSdk 34) |
| `releases/KwataTracker.apk` | Prebuilt Android APK |
| `assets/` | Logo |

## Build the Android app
```bash
cd android-app
npm install
npx cap copy android
cd android && ./gradlew assembleDebug
```
The APK lands in `android/app/build/outputs/apk/debug/`.

## Backend & security
Supabase (Postgres with row-level security and realtime, anonymous sign-in). The key in the app is the public publishable key; access is enforced by RLS. No secret keys are in this repo — see [SECURITY.md](SECURITY.md).

---
© Kwata Enterprise · Motswana Intelligence. Founded by Thuto Leseane.
