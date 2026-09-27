# MileTrace — Phase 1 Android

**Every distance, traced live.**

This repository is the upload-ready source for the first native Android phase.

## Included
- Native Android app
- Real device GPS using Android LocationManager
- Live distance calculation from successive GPS fixes
- GPS accuracy filtering and unreasonable-jump rejection
- Walking / Running / Cycling / Bike modes
- Start / Pause / Resume / End
- Live speed, elapsed time, GPS accuracy and coordinates
- Local journey history
- Route point storage
- Offline core after installation
- No account, backend, cloud or paid service

## Build
Open the project in Android Studio and let Gradle sync. The project uses:
- Android Gradle Plugin 8.6.1
- compileSdk 35
- minSdk 26
- targetSdk 35

A local Android SDK/Gradle environment is required to produce the APK.

## Real-time requirement
The distance is not simulated. The app listens to the phone's location providers and calculates geographic distance between validated fixes.

GPS quality varies with the device, permissions, sky visibility, buildings and operating-system power management.

## Privacy
Location permission is requested only when the user starts a journey. Journey data is stored locally in the app's private preferences. No cloud upload is implemented in Phase 1.

## Important limitation
This ZIP contains source/build configuration and is ready to upload to a Git repository. The final APK still needs an Android build environment (Android Studio/SDK + Gradle) to compile and install.
