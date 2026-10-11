# SmartDrivo Admin Android — V1 source

A separate native Android/Jetpack Compose APK. **Never install it over the driver APK**:

- Driver: `com.smartdrivo.app` (existing, unchanged)
- Admin: `com.smartdrivo.admin` (new)

## Features

- Firebase email/password + optional Google sign-in (requires Android OAuth configured)
- Verifies `users/{auth.uid}.isAdmin == true` and `isActive == true` from the **SERVER** at login, on updates and before writes
- Dashboard, drivers search, activate/deactivate, live payments, approve/reject
- Payments: same existing web-admin transaction workflow: legacy UTR search, immutable `/paymentUtrs/{utr}` reservation, payment approval + user membership extension in a single Firestore transaction
- Confirmation requiring the final four UTR digits on approval
- UPI and 3/7/15/30-day plan pricing from `/settings/global`
- Admin action audit logs in `/logs`
- `FLAG_SECURE`, no Android backup, no local Firestore offline cache, no static passwords or admin-list emails hardcoded

## Mandatory Firebase setup — one time

In Firebase Console, project **smartdrivo**, add a NEW Android app:

**Package name: `com.smartdrivo.admin`**

Download that Android app's `google-services.json` into:

`admin-android/app/google-services.json`

Do **not** copy the driver app's `google-services.json`; it belongs to another package. This file is ignored by Git. Add SHA-1/SHA-256 certificate fingerprints and a Web OAuth client in Firebase/Google Cloud to enable **Continue with Google**. Until then, the admin's existing email/password authentication can be used if enabled in Firebase Authentication.

The Firebase Auth UID must have its own `/users/{uid}` with BOTH `isAdmin: true` and `isActive: true`. There is deliberately no email-only fallback or local admin override.

## Build on user's PC

From `C:\Users\ADMIN\AndroidStudioProjects\smartdrivo`:

```powershell
& .\gradlew.bat -p .\admin-android :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Install *internal test APK* at:

`admin-android/app/build/outputs/apk/debug/app-debug.apk`

Debug APK is for private testing, not Telegram distribution. Sign the release with a separate, securely stored admin keystore. Firebase OAuth SHA fingerprints must match the signing certificate used to install the app.

## Optional GitHub Actions

The workflow `.github/workflows/admin-android.yml` is **manual-only** and does not affect the existing SmartDrivo driver build. For remote internal testing, add repository secret `ADMIN_FIREBASE_JSON_BASE64` containing the base64-encoded new admin `google-services.json`. The workflow is manual-only. GitHub may require the workflow YAML to exist on the repository default branch before its manual Run button appears. Until that is set up, use the local Gradle command above. Never send the generated private testing APK to drivers.

## Before production — SECURITY REQUIRED

**Existing repo Firestore security rules have an issue**: `function isAdmin()` grants admin operations based on `isAdmin == true` OR a token claim and does not require `isActive == true` consistently. A client-side check is NOT a security boundary. Review and deploy the separately supplied `firestore-admin-hardening.rules` only after ensuring the admin account has `isActive: true` and verifying all existing admin flows. Do not blindly overwrite/deploy rules, or accidentally lock out existing web admins.

**No production-readiness claim** until the separate Firebase app is registered, server-side rules are audited, debug APK is tested, and a separately signed release is verified. This code was prepared from the public repo schema; its Android build needs to run with your Firebase config on your PC.

## Limits of V1

The driver and payment lists sync up to 200 documents for responsiveness. Add server-backed pagination and search for larger deployments. Google OAuth needs the separate new package and certificate setup. The current driver APK and GitHub `main` are deliberately unchanged.
