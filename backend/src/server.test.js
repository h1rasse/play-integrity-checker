import test from "node:test";
import assert from "node:assert/strict";
import { mockVerdict, validateInput } from "./server.js";

test("rejects missing token", () => {
  assert.equal(validateInput({ requestHash: "valid-request-hash" }), "integrityToken is required");
});

test("rejects a package mismatch", () => {
  const result = validateInput({
    integrityToken: "a-token-that-is-long-enough",
    requestHash: "valid-request-hash",
    packageName: "com.other.app",
  });
  assert.equal(result, "packageName does not match server configuration");
});

test("creates an explicitly labeled mock verdict", () => {
  const verdict = mockVerdict({ requestHash: "valid-request-hash" });
  assert.equal(verdict.verificationMode, "mock");
  assert.equal(verdict.isValid, true);
  assert.match(verdict.warnings[0], /mock response/);
});
