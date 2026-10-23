# Architecture

The project has two trust boundaries:

1. The Android app requests a single-use, cryptographically random nonce from the backend.
2. The Android app requests a Classic Play Integrity token using that nonce and sends the opaque token to the backend.
3. The backend, not the Android app, decodes the token through Google's Play Integrity API and evaluates the returned payload.

The backend stores the nonce before returning it and consumes it when verification starts. It compares the decoded `requestDetails.nonce` and `requestDetails.requestPackageName` with the expected values, then checks freshness and verdict policy. Production applications should store nonces in a durable, shared store when multiple backend instances are used, and bind them to an authenticated session or action.

`MOCK_MODE=true` exists only for local development and UI demonstrations. It never proves device integrity. Real verification requires a linked Google Cloud project, a service account with the Play Integrity scope, and a package name registered with Google Play.

The sample deliberately does not implement account enforcement, replay storage, rate limiting, or application-specific policy. Those are production responsibilities and should be designed around the protected action rather than a single global allow/deny value.
