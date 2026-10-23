import "dotenv/config";
import http from "node:http";
import { GoogleAuth } from "google-auth-library";

const port = Number(process.env.PORT || 8080);
const packageName = process.env.ANDROID_PACKAGE_NAME || "com.h1rasse.playintegritychecker";
const mockMode = String(process.env.MOCK_MODE || "true").toLowerCase() === "true";
const maxTokenAgeMs = Number(process.env.MAX_TOKEN_AGE_MS || 120_000);

function sendJson(response, statusCode, body) {
  const payload = JSON.stringify(body, null, 2);
  response.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
  });
  response.end(payload);
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) {
        reject(new Error("Request body is too large"));
        request.destroy();
      }
    });
    request.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error("Request body must be valid JSON"));
      }
    });
    request.on("error", reject);
  });
}

function validateInput(input) {
  if (!input || typeof input !== "object") {
    return "Request body must be an object";
  }
  if (typeof input.integrityToken !== "string" || input.integrityToken.length < 20) {
    return "integrityToken is required";
  }
  if (typeof input.requestHash !== "string" || input.requestHash.length < 10) {
    return "requestHash is required";
  }
  if (input.packageName !== undefined && input.packageName !== packageName) {
    return "packageName does not match server configuration";
  }
  return null;
}

function mockVerdict(input) {
  return {
    verificationMode: "mock",
    isValid: true,
    requestDetails: {
      requestPackageName: packageName,
      requestHash: input.requestHash,
      timestampMillis: String(Date.now()),
    },
    appIntegrity: {
      appRecognitionVerdict: "PLAY_RECOGNIZED",
      packageName,
      certificateSha256Digest: ["demo-only-not-a-real-certificate"],
      versionCode: "1",
    },
    accountDetails: { appLicensingVerdict: "LICENSED" },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    warnings: ["This is a local mock response. It is not a Google-issued verdict."],
  };
}

async function decodeIntegrityToken(input) {
  const auth = new GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/playintegrity"],
  });
  const client = await auth.getClient();
  const accessTokenResult = await client.getAccessToken();
  const accessToken = typeof accessTokenResult === "string" ? accessTokenResult : accessTokenResult.token;
  if (!accessToken) {
    throw new Error("Unable to obtain a Google Cloud access token");
  }

  const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`;
  const googleResponse = await fetch(url, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ integrity_token: input.integrityToken }),
  });
  const payload = await googleResponse.json();
  if (!googleResponse.ok) {
    const error = new Error(payload?.error?.message || "Google token decoding failed");
    error.statusCode = googleResponse.status;
    throw error;
  }

  const tokenPayload = payload.tokenPayloadExternal;
  const requestDetails = tokenPayload?.requestDetails;
  const requestMatches = requestDetails?.requestPackageName === packageName &&
    requestDetails?.requestHash === input.requestHash;
  const timestampMillis = Number(requestDetails?.timestampMillis);
  const ageMs = Date.now() - timestampMillis;
  const timestampFresh = Number.isFinite(timestampMillis) && ageMs >= 0 && ageMs <= maxTokenAgeMs;
  const appRecognition = tokenPayload?.appIntegrity?.appRecognitionVerdict;
  const licensing = tokenPayload?.accountDetails?.appLicensingVerdict;
  const deviceLabels = tokenPayload?.deviceIntegrity?.deviceRecognitionVerdict || [];
  const appRecognized = appRecognition === "PLAY_RECOGNIZED";
  const licensed = licensing === "LICENSED";
  const deviceEvaluated = deviceLabels.length > 0;
  const warnings = [];
  if (!requestMatches) warnings.push("Decoded request details do not match the expected package or request hash.");
  if (!timestampFresh) warnings.push(`Token timestamp is outside the ${maxTokenAgeMs} ms freshness window.`);
  if (!appRecognized) warnings.push(`Application recognition verdict is ${appRecognition || "missing"}.`);
  if (!licensed) warnings.push(`Application licensing verdict is ${licensing || "missing"}.`);
  if (!deviceEvaluated) warnings.push("No device recognition label was returned.");
  return {
    verificationMode: "google",
    isValid: Boolean(requestMatches && timestampFresh && appRecognized && licensed && deviceEvaluated),
    requestMatches,
    timestampFresh,
    ageMs,
    appRecognized,
    licensed,
    deviceEvaluated,
    requestDetails,
    appIntegrity: tokenPayload?.appIntegrity,
    accountDetails: tokenPayload?.accountDetails,
    deviceIntegrity: tokenPayload?.deviceIntegrity,
    environmentDetails: tokenPayload?.environmentDetails,
    warnings,
  };
}

async function handleVerify(request, response) {
  try {
    const input = await readJson(request);
    const validationError = validateInput(input);
    if (validationError) {
      sendJson(response, 400, { error: validationError });
      return;
    }
    if (mockMode) {
      sendJson(response, 200, mockVerdict(input));
      return;
    }
    sendJson(response, 200, await decodeIntegrityToken(input));
  } catch (error) {
    console.error(error);
    sendJson(response, error.statusCode || 500, {
      error: "Verification failed",
      message: error.message,
    });
  }
}

const server = http.createServer((request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    sendJson(response, 200, { status: "ok", verificationMode: mockMode ? "mock" : "google" });
    return;
  }
  if (request.method === "POST" && request.url === "/api/verify") {
    handleVerify(request, response);
    return;
  }
  sendJson(response, 404, { error: "Not found" });
});

if (process.env.NODE_ENV !== "test") {
  server.listen(port, () => {
    console.log(`Play Integrity verifier listening on http://localhost:${port}`);
    console.log(`Verification mode: ${mockMode ? "mock" : "google"}`);
  });
}

export { server, mockVerdict, validateInput };
