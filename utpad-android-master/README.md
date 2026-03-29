# GUDGUM_PROD_FLOW Android App

## Project Folder

Open this folder in Android Studio:

`C:\Users\devad\Downloads\Good_Flow\GUDGUM_PROD_FLOW`

## Run Commands

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

Debug APK output:

`app\build\outputs\apk\debug\app-debug.apk`

## Current Mobile Scope

- Worker-only Android login (admin login removed from mobile flow).
- Single-factory operation flow.
- Strict role-based module authorization:
  - Inwarding credential can only open Inwarding.
  - Production credential can only open Production.
  - Packing credential can only open Packing.
  - Dispatch credential can only open Dispatch.
- Barcode scanning wired with camera permission (ZXing embedded).
- Module submit APIs now post to shared backend event stream (`/api/v1/ops/events`).

## Backend Event Stream Requirement

- Mobile submit sync target is configured at:
  - `app/build.gradle.kts` -> `BuildConfig.OPS_API_BASE_URL`
  - Current: `https://gudgumerp.vercel.app/api/v1/`
- Shared Supabase project metadata is configured at:
  - `BuildConfig.SUPABASE_API_URL`
  - `BuildConfig.SUPABASE_PROJECT_REF`
  - `BuildConfig.SUPABASE_PUBLISHABLE_KEY`
- Required backend endpoints:
  - `POST /api/v1/ops/events`
  - `GET /api/v1/ops/events`
  - `GET /api/v1/ops/events/stream`

## Test Credentials (Worker Login)

- `9876543210 / 123456` (Inwarding Staff)
- `9876543211 / 223344` (Production Operator)
- `9876543212 / 112233` (Packing Staff)
- `9876543213 / 654321` (Dispatch Staff)

You can also auto-fill these from the login screen "Test Credentials" chips.
