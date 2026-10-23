# Architecture

The project has two trust boundaries:

1. The Android app requests a Standard Play Integrity token and sends the opaque token to the backend.
2. The backend, not the Android app, decodes the token through Google's Play Integrity API and evaluates the returned payload.

The Android app is responsible for creating a request hash that binds the token to the action being checked. The backend must compare the decoded `requestDetails.requestHash` and `requestDetails.requestPackageName` with values it expected for that request. Production applications should derive the expected request hash from server-controlled state, such as an authenticated session and a single-use action challenge.

`MOCK_MODE=true` exists only for local development and UI demonstrations. It never proves device integrity. Real verification requires a linked Google Cloud project, a service account with the Play Integrity scope, and a package name registered with Google Play.

The sample deliberately does not implement account enforcement, replay storage, rate limiting, or application-specific policy. Those are production responsibilities and should be designed around the protected action rather than a single global allow/deny value.
