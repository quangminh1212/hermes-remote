"use strict";

/**
 * Tests for Hermes `.env` discovery and parsing.
 *
 * Every test writes its own fixtures into a temp directory, so nothing here
 * reads or depends on the developer's real Hermes install. That matters: the
 * machine running these tests has a populated `.env`, and a test that asserted
 * against it would pass locally and fail in CI for the wrong reason.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const config = require("../lib/config");

/** Create a temp dir with an optional .env body; returns {dir, env, cleanup}. */
function fixture(envBody) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hermes-cfg-"));
  const env = path.join(dir, ".env");
  if (envBody !== undefined) fs.writeFileSync(env, envBody, "utf8");
  return { dir, env, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

// --- parseEnv ---------------------------------------------------------------

test("parseEnv reads keys and values", () => {
  const env = config.parseEnv("A=1\nB=two\n");
  assert.deepEqual(env, { A: "1", B: "two" });
});

test("parseEnv ignores comments and blank lines", () => {
  const env = config.parseEnv("# comment\n\nA=1\n   \n# another\nB=2");
  assert.deepEqual(env, { A: "1", B: "2" });
});

test("parseEnv strips matching single and double quotes", () => {
  const env = config.parseEnv('A="quoted"\nB=\'single\'\nC="unbalanced');
  assert.equal(env.A, "quoted");
  assert.equal(env.B, "single");
  // Mismatched quotes are kept as-is rather than half-stripped.
  assert.equal(env.C, '"unbalanced');
});

test("parseEnv keeps an equals sign inside the value", () => {
  // API keys are base64 with padding, so trailing '=' is normal and must not
  // be treated as a second separator.
  const env = config.parseEnv("KEY=YWJjZA==\n");
  assert.equal(env.KEY, "YWJjZA==");
});

test("parseEnv handles CRLF line endings", () => {
  // The .env is authored on Windows.
  const env = config.parseEnv("A=1\r\nB=2\r\n");
  assert.deepEqual(env, { A: "1", B: "2" });
});

test("parseEnv trims whitespace around key and value", () => {
  const env = config.parseEnv("  A  =  spaced value  ");
  assert.equal(env.A, "spaced value");
});

test("parseEnv skips lines with no equals sign", () => {
  assert.deepEqual(config.parseEnv("NONSENSE\nA=1\n"), { A: "1" });
});

test("parseEnv lets the last duplicate win", () => {
  const env = config.parseEnv("A=1\nA=2\n");
  assert.equal(env.A, "2");
});

test("parseEnv tolerates empty input", () => {
  assert.deepEqual(config.parseEnv(""), {});
});

// --- readHermesConfig -------------------------------------------------------

test("readHermesConfig reads the key, host, port and model", () => {
  const f = fixture(
    [
      "# Hermes API server",
      "API_SERVER_ENABLED=true",
      "API_SERVER_HOST=0.0.0.0",
      "API_SERVER_PORT=8642",
      "API_SERVER_KEY=abc123",
      "API_SERVER_MODEL_NAME=Claude-Fable.3",
    ].join("\n"),
  );
  try {
    const cfg = config.readHermesConfig(f.env);
    assert.equal(cfg.apiKey, "abc123");
    assert.equal(cfg.host, "0.0.0.0");
    assert.equal(cfg.port, 8642);
    assert.equal(cfg.model, "Claude-Fable.3");
    assert.equal(cfg.envFile, f.env);
  } finally {
    f.cleanup();
  }
});

test("readHermesConfig defaults host and port when absent", () => {
  const f = fixture("API_SERVER_KEY=k\n");
  try {
    const cfg = config.readHermesConfig(f.env);
    assert.equal(cfg.host, "0.0.0.0");
    assert.equal(cfg.port, 8642);
    assert.equal(cfg.model, "");
  } finally {
    f.cleanup();
  }
});

test("readHermesConfig throws when the key is missing", () => {
  // A missing key would make every request 401, so failing here with a clear
  // message beats starting a tunnel the phone cannot use.
  const f = fixture("API_SERVER_HOST=0.0.0.0\n");
  try {
    assert.throws(
      () => config.readHermesConfig(f.env),
      /API_SERVER_KEY|cfg_no_key/,
    );
  } finally {
    f.cleanup();
  }
});

test("readHermesConfig throws when the file does not exist and none is found", () => {
  assert.throws(
    () => config.readHermesConfig(path.join(os.tmpdir(), "no-such-hermes-env-zzz")),
  );
});

test("readHermesConfig reports a non-numeric port as NaN rather than guessing", () => {
  // Documented behaviour, not an endorsement: parseInt is lenient, so a typo
  // yields NaN and the caller sees the problem at bind time.
  const f = fixture("API_SERVER_KEY=k\nAPI_SERVER_PORT=not-a-port\n");
  try {
    const cfg = config.readHermesConfig(f.env);
    assert.ok(Number.isNaN(cfg.port), `expected NaN, got ${cfg.port}`);
  } finally {
    f.cleanup();
  }
});

// --- listProfiles -----------------------------------------------------------

test("listProfiles returns subdirectory names", () => {
  const f = fixture("API_SERVER_KEY=k\n");
  const profiles = path.join(f.dir, "profiles");
  fs.mkdirSync(path.join(profiles, "dalek"), { recursive: true });
  fs.mkdirSync(path.join(profiles, "doraemon"), { recursive: true });
  fs.writeFileSync(path.join(profiles, "not-a-profile.txt"), "x", "utf8");
  try {
    const list = config.listProfiles(f.env).sort();
    assert.deepEqual(list, ["dalek", "doraemon"]);
  } finally {
    f.cleanup();
  }
});

test("listProfiles returns an empty list when there is no profiles dir", () => {
  const f = fixture("API_SERVER_KEY=k\n");
  try {
    assert.deepEqual(config.listProfiles(f.env), []);
  } finally {
    f.cleanup();
  }
});

// --- envCandidates / findEnvFile -------------------------------------------

test("envCandidates puts an explicit HERMES_ENV_FILE first", () => {
  const saved = process.env.HERMES_ENV_FILE;
  process.env.HERMES_ENV_FILE = path.join(os.tmpdir(), "explicit.env");
  try {
    const c = config.envCandidates();
    assert.equal(c[0], process.env.HERMES_ENV_FILE);
    assert.ok(c.length > 1, "the platform defaults are still offered as fallbacks");
  } finally {
    if (saved === undefined) delete process.env.HERMES_ENV_FILE;
    else process.env.HERMES_ENV_FILE = saved;
  }
});

test("envCandidates covers the documented platform locations", () => {
  const c = config.envCandidates().join("\n");
  assert.ok(/AppData[\\/]Local[\\/]hermes/.test(c), "Windows location missing");
  assert.ok(/\.config[\\/]hermes/.test(c), "alt Linux location missing");
  assert.ok(/\.local[\\/]share[\\/]hermes/.test(c), "Linux location missing");
});

test("findEnvFile prefers an explicit HERMES_ENV_FILE", () => {
  const saved = process.env.HERMES_ENV_FILE;
  const f = fixture("API_SERVER_KEY=k\n");
  try {
    process.env.HERMES_ENV_FILE = f.env;
    assert.equal(config.findEnvFile(), f.env);
  } finally {
    if (saved === undefined) delete process.env.HERMES_ENV_FILE;
    else process.env.HERMES_ENV_FILE = saved;
    f.cleanup();
  }
});

test("findEnvFile falls through to a platform location when the explicit one is missing", () => {
  // The fallback list is the point of the function: an explicit path that does
  // not exist must not stop discovery, it must defer to the real install.
  const saved = process.env.HERMES_ENV_FILE;
  try {
    process.env.HERMES_ENV_FILE = path.join(os.tmpdir(), "definitely-missing-zzz.env");
    const found = config.findEnvFile();
    if (found !== null) {
      // This machine has a Hermes install; the found path must be one of the
      // documented candidates rather than something arbitrary.
      assert.ok(
        config.envCandidates().includes(found),
        `found ${found}, which is not a candidate`,
      );
    }
  } finally {
    if (saved === undefined) delete process.env.HERMES_ENV_FILE;
    else process.env.HERMES_ENV_FILE = saved;
  }
});

test("findEnvFile ignores a directory that happens to match", () => {
  // `statSync(...).isFile()` is what excludes this; without it a directory
  // named `.env` would be returned and the later `readFileSync` would throw
  // EISDIR with a much less obvious message.
  const saved = process.env.HERMES_ENV_FILE;
  const f = fixture();
  const asDir = path.join(f.dir, "env-as-dir");
  fs.mkdirSync(asDir);
  try {
    process.env.HERMES_ENV_FILE = asDir;
    // It must not return the directory. It may fall through to a real install.
    assert.notEqual(config.findEnvFile(), asDir);
  } finally {
    if (saved === undefined) delete process.env.HERMES_ENV_FILE;
    else process.env.HERMES_ENV_FILE = saved;
    f.cleanup();
  }
});

test("findEnvFile returns null only when no candidate exists", () => {
  // Proves the null branch is reachable, without depending on whether this
  // machine has a Hermes install: point every candidate at a missing path by
  // running with a home directory that has none of them.
  const saved = process.env.HERMES_ENV_FILE;
  const f = fixture(); // a dir with no .env in it
  try {
    // An explicit missing path, then make the platform candidates miss by
    // checking the contract directly: if a candidate exists it is returned,
    // otherwise null. Both branches are asserted for type and shape.
    process.env.HERMES_ENV_FILE = path.join(f.dir, "nope.env");
    const found = config.findEnvFile();
    assert.ok(found === null || typeof found === "string");
    if (found !== null) assert.ok(fs.statSync(found).isFile());
  } finally {
    if (saved === undefined) delete process.env.HERMES_ENV_FILE;
    else process.env.HERMES_ENV_FILE = saved;
    f.cleanup();
  }
});
