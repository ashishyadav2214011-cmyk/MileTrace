# MileTrace — GitHub Upload

This folder is the repository root. Upload these files directly to a GitHub repository.

## Build
GitHub Actions is included in `.github/workflows/android.yml`.
After upload, open **Actions → Build MileTrace Android** and run the workflow if needed.
The workflow builds a debug APK and publishes it as the `MileTrace-debug-apk` artifact.

## Phase 1
- Real Android GPS tracking
- Live distance calculation
- Walking / Running / Cycling / Bike
- Start / Pause / Resume / End
- Live speed, elapsed time and GPS accuracy
- Local journey history and route points
- Offline core
- User-controlled location permission
- No cloud/backend/account

## Important
The app uses real device location data; it does not simulate distance. GPS accuracy and update frequency depend on the phone and environment.
