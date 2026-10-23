# Play Integrity Checker

A reference Android and Node.js project for requesting and verifying Google Play Integrity API verdicts.

The repository demonstrates the correct client-server boundary: the Android application requests an opaque Standard Play Integrity token, while the backend decodes the token through Google's API and evaluates the returned payload. A clearly labeled mock mode is included so the project can be demonstrated without Google Cloud credentials.

## What this project checks

The verifier exposes the core Play Integrity signals returned by Google:

- Request package name and request hash
- Application recognition verdict
- Application package name, certificate digest, and version code
- Application licensing verdict
- Device recognition verdict labels
- Environment details when present

The project does not claim that a local root check can replace Play Integrity. Device integrity is a tiered server-side signal. A production application should combine it with request binding, authentication, rate limits, replay protection, and an action-specific enforcement policy.

## Repository structure

```text
.
├── android-app/        # Kotlin Android client
├── backend/            # Node.js token verifier
├── docs/               # Architecture notes
├── .gitignore
└── README.md
```

## Local demo mode

Mock mode is enabled by default and does not contact Google. It is useful for testing the backend contract and demonstrating the Android flow, but its verdict is not evidence about a real device.

Run the backend:

```bash
cd backend
cp .env.example .env
npm install
npm test
npm start
```

Check the health endpoint:

```bash
curl http://localhost:8080/health
```

Example mock verification request:

```bash
curl -X POST http://localhost:8080/api/verify \
  -H 'content-type: application/json' \
  -d '{
    "integrityToken": "demo-token-with-at-least-20-characters",
    "requestHash": "demo-request-hash",
    "packageName": "com.h1rasse.playintegritychecker"
  }'
```

The response includes `verificationMode: "mock"` and a warning that the verdict is not Google-issued.

## Android setup

Open `android-app` in Android Studio and allow Gradle to sync. The Android module uses the official Play Integrity library:

```text
com.google.android.play:integrity:1.6.0
```

Before requesting a real token, set `cloudProjectNumber` in `MainActivity.kt` to the numeric Google Cloud project number associated with the app. The sample then prepares a Standard Integrity token provider and requests a token with a SHA-256 request hash.

The app sends the returned opaque token, request hash, and package name to `POST /api/verify` over the configured HTTPS backend URL. Build with the backend URL as a Gradle property:

```bash
gradle --no-daemon assembleDebug -PbackendBaseUrl=https://your-backend.example.com
```

## Real Google verification

To enable real verification:

1. Create or select a Google Cloud project.
2. Enable the Play Integrity API.
3. Link the Cloud project to the app in Google Play Console.
4. Create a service account for the backend and grant only the permissions required by your deployment.
5. Store the service-account credentials outside Git and set `GOOGLE_APPLICATION_CREDENTIALS` to the JSON file path.
6. Set `MOCK_MODE=false` and configure `ANDROID_PACKAGE_NAME`.
7. Deploy the backend behind HTTPS.
8. Send each real token to `POST /api/verify` with the request hash generated for the same action.

Example environment:

```bash
export MOCK_MODE=false
export ANDROID_PACKAGE_NAME=com.example.yourapp
export GOOGLE_APPLICATION_CREDENTIALS=/secure/path/play-integrity-service-account.json
npm start
```

The backend calls Google's `decodeIntegrityToken` endpoint, then checks that the decoded package name and request hash match, the token timestamp is fresh, the app is `PLAY_RECOGNIZED`, the account is `LICENSED`, and at least one device recognition label is present. This checker is intended for diagnostics. It does not persist used challenges or implement a full user session. Add those controls before protecting a valuable action.

## Security boundaries

Do not embed Google Cloud service-account credentials in the Android application. Do not decode or trust an integrity token only on the device. Do not treat `MEETS_BASIC_INTEGRITY` as equivalent to `MEETS_STRONG_INTEGRITY`. Do not cache a verdict for unrelated actions. Do not publish real credentials, certificate keys, or Play Console configuration in this repository.

A Play Integrity verdict is one input to an abuse-defense policy. It is not a general-purpose malware scanner, a guarantee that an app is safe, or a replacement for authentication and authorization.

## Testing

Backend tests run without network access or Google credentials:

```bash
cd backend
npm test
```

The Android module can be built from Android Studio or with a configured Gradle installation:

```bash
cd android-app
./gradlew assembleDebug
```

## GitHub Actions build

Every push to `main` and every pull request runs backend tests and builds a debug APK. The APK is published as the `play-integrity-checker-debug-apk` workflow artifact. The workflow uses a placeholder backend URL, so rebuild the artifact with `-PbackendBaseUrl=https://your-backend.example.com` before installing it for real checks.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
