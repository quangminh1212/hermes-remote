"use strict";

/**
 * Manage a Cloudflare quick tunnel (cloudflared) to expose the local Hermes API
 * to the internet, so the phone can connect from anywhere (4G / another Wi-Fi).
 *
 * Downloads the cloudflared binary on first use, starts `cloudflared tunnel
 * --url <local>`, and scrapes the printed https://<random>.trycloudflare.com URL.
 */

const fs = require("fs");
const os = require("os");
const path = require("path");
const https = require("https");
const { spawn, spawnSync } = require("child_process");

/** Where we cache the downloaded binary. */
function binDir() {
  return path.join(os.homedir(), ".hermes-remote", "bin");
}

function platformAsset() {
  const plat = process.platform;
  const arch = process.arch;
  let base = "cloudflared-";
  if (plat === "win32") base += "windows-amd64.exe";
  else if (plat === "darwin") base += arch === "arm64" ? "darwin-arm64.tgz" : "darwin-amd64.tgz";
  else base += arch === "arm64" ? "linux-arm64" : "linux-amd64";
  return base;
}

function localBinaryName() {
  return process.platform === "win32" ? "cloudflared.exe" : "cloudflared";
}

/** Return a usable cloudflared path: found on PATH, else our cached download. */
function resolveCloudflared() {
  // Already on PATH?
  const which = spawnSync(process.platform === "win32" ? "where" : "which", [
    "cloudflared",
  ]);
  if (which.status === 0) {
    const line = which.stdout.toString().split(/\r?\n/)[0].trim();
    if (line) return line;
  }
  const cached = path.join(binDir(), localBinaryName());
  if (fs.existsSync(cached)) return cached;
  return null;
}

function download(url, dest, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 6) return reject(new Error("too many redirects"));
    https
      .get(url, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          return resolve(download(res.headers.location, dest, redirects + 1));
        }
        if (res.statusCode !== 200) {
          res.resume();
          return reject(new Error("HTTP " + res.statusCode + " for " + url));
        }
        const tmp = dest + ".part";
        const out = fs.createWriteStream(tmp);
        res.pipe(out);
        out.on("finish", () => {
          out.close(() => {
            fs.renameSync(tmp, dest);
            resolve(dest);
          });
        });
        out.on("error", reject);
      })
      .on("error", reject);
  });
}

/** Download and cache the cloudflared binary. Returns its path. */
async function ensureCloudflared(log = () => {}) {
  const existing = resolveCloudflared();
  if (existing) return existing;

  fs.mkdirSync(binDir(), { recursive: true });
  const asset = platformAsset();
  const base = `https://github.com/cloudflare/cloudflared/releases/latest/download/`;
  const isTgz = asset.endsWith(".tgz");
  const target = path.join(binDir(), isTgz ? "cloudflared.tgz" : localBinaryName());

  log("Đang tải cloudflared (" + asset + ")...");
  await download(base + asset, target);

  if (isTgz) {
    log("Giải nén cloudflared...");
    const r = spawnSync("tar", ["xzf", target, "-C", binDir()]);
    if (r.status !== 0) throw new Error("giải nén cloudflared thất bại");
    fs.rmSync(target, { force: true });
  }

  const bin = path.join(binDir(), localBinaryName());
  if (!fs.existsSync(bin)) throw new Error("không thấy cloudflared sau khi tải");
  if (process.platform !== "win32") fs.chmodSync(bin, 0o755);
  return bin;
}

/**
 * Start a quick tunnel to localUrl. Resolves with { url, proc } once the public
 * URL appears in cloudflared's output (or rejects on timeout).
 */
function startTunnel(localUrl, binPath, timeoutMs = 45000) {
  return new Promise((resolve, reject) => {
    const proc = spawn(binPath, ["tunnel", "--url", localUrl, "--no-autoupdate"], {
      stdio: ["ignore", "pipe", "pipe"],
    });

    let done = false;
    const timer = setTimeout(() => {
      if (done) return;
      done = true;
      try {
        proc.kill();
      } catch (_) {}
      reject(new Error("Hết thời gian chờ URL tunnel"));
    }, timeoutMs);

    const onData = (buf) => {
      const text = buf.toString();
      const m = text.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/);
      if (m && !done) {
        done = true;
        clearTimeout(timer);
        resolve({ url: m[0], proc });
      }
    };

    proc.stdout.on("data", onData);
    proc.stderr.on("data", onData);
    proc.on("error", (err) => {
      if (!done) {
        done = true;
        clearTimeout(timer);
        reject(err);
      }
    });
    proc.on("exit", (code) => {
      if (!done) {
        done = true;
        clearTimeout(timer);
        reject(new Error("cloudflared thoát sớm (code " + code + ")"));
      }
    });
  });
}

module.exports = { ensureCloudflared, startTunnel, resolveCloudflared, binDir };
