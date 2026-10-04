"use strict";

/**
 * Locate and parse the Hermes .env file so we can read the API server key and
 * host/port without the user having to copy-paste anything.
 */

const fs = require("fs");
const os = require("os");
const path = require("path");

/** Candidate locations for the Hermes .env, in priority order. */
function envCandidates() {
  const home = os.homedir();
  const out = [];
  if (process.env.HERMES_ENV_FILE) out.push(process.env.HERMES_ENV_FILE);
  out.push(path.join(home, "AppData", "Local", "hermes", ".env")); // Windows
  out.push(path.join(home, ".local", "share", "hermes", ".env")); // Linux
  out.push(path.join(home, ".config", "hermes", ".env")); // alt Linux
  out.push(path.join(home, ".hermes", ".env")); // generic
  return out;
}

/** Find the first existing .env path, or null. */
function findEnvFile() {
  for (const p of envCandidates()) {
    try {
      if (fs.statSync(p).isFile()) return p;
    } catch (_) {
      /* keep looking */
    }
  }
  return null;
}

/** Parse KEY=VALUE lines; ignore comments and blanks. Strips surrounding quotes. */
function parseEnv(text) {
  const out = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

/** Read the Hermes config: apiKey, host, port, model, profiles. */
function readHermesConfig(envPath) {
  const file = envPath || findEnvFile();
  if (!file) {
    throw new Error(
      "Không tìm thấy file .env của Hermes. Đặt biến HERMES_ENV_FILE hoặc cài Hermes trước."
    );
  }
  const raw = fs.readFileSync(file, "utf8");
  const env = parseEnv(raw);

  const apiKey = env.API_SERVER_KEY || "";
  const host = env.API_SERVER_HOST || "0.0.0.0";
  const port = parseInt(env.API_SERVER_PORT || "8642", 10);
  const model = env.API_SERVER_MODEL_NAME || "";

  if (!apiKey) {
    throw new Error(
      "API_SERVER_KEY trống trong " + file + " — bật API server Hermes trước."
    );
  }

  return { envFile: file, apiKey, host, port, model };
}

/** List Hermes profiles (subdirectories of profiles/ next to the .env). */
function listProfiles(envFile) {
  const dir = path.join(path.dirname(envFile), "profiles");
  try {
    return fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name);
  } catch (_) {
    return [];
  }
}

module.exports = { findEnvFile, parseEnv, readHermesConfig, listProfiles, envCandidates };
