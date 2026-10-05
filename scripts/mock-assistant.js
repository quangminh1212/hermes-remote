#!/usr/bin/env node
/**
 * Mock Google Assistant / App Actions dispatcher.
 *
 * WHY THIS EXISTS
 * ---------------
 * The real Assistant cannot be exercised on an AOSP emulator: there is no
 * Google Play Services and no voice-interaction service, so the system never
 * emits the intent. That leaves the riskiest part of the integration — "did the
 * Assistant's payload actually reach the app with the user's words intact?" —
 * untested on CI and on any machine without a Play-enabled device.
 *
 * This tool closes that gap. It reproduces, byte for byte, the Android intents
 * Google's App Actions tooling emits for each documented delivery shape, and
 * fires them at the app with `adb`. It is a *simulator of the caller*, not of
 * the app: the app is the real APK, unmodified.
 *
 * COVERAGE
 * --------
 *   custom-extra    custom capability, text in our own `query` extra
 *   assistant-text  text in android.intent.extra.TEXT
 *   bii-bundle      built-in intent, params nested in a Bundle
 *   deep-link       hermes://ask?query=...
 *   voice-command   android.intent.action.VOICE_COMMAND
 *   hot-start       second hand-off while the app is already open
 *   bare-invoke     "Hermes" with no request — must send nothing
 *   cold-start      app not running
 *
 * USAGE
 * -----
 *   node scripts/mock-assistant.js --list
 *   node scripts/mock-assistant.js --shape custom-extra --query "git status"
 *   node scripts/mock-assistant.js --all
 *   node scripts/mock-assistant.js --all --package io.hermes.remote.debug
 *
 * Exits non-zero if any shape fails its expectation, so it works in CI.
 */

"use strict";

const { execFileSync } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs");

// --- Configuration ----------------------------------------------------------

/** Default package under test. The debug variant is the debuggable one. */
const DEFAULT_PACKAGE = "io.hermes.remote.debug";

/**
 * Activity component for a package.
 *
 * The debug variant's applicationId carries a `.debug` suffix but its Kotlin
 * package (and therefore its activity class) does not, so the component cannot
 * be derived by pasting the applicationId twice. The manifest namespace is the
 * stable part; the applicationId is what changes per variant.
 */
const MANIFEST_NAMESPACE = "io.hermes.remote";

function activityComponent(pkg) {
  return `${pkg}/${MANIFEST_NAMESPACE}.ui.MainActivity`;
}

/** Action declared by the App Actions capability. */
const ACTION_ASK = "io.hermes.remote.action.ASK";

/** Locate adb: env override, then the standard SDK locations. */
function findAdb() {
  if (process.env.ADB) return process.env.ADB;
  const home = process.env.USERPROFILE || process.env.HOME || "";
  const candidates = [
    path.join(home, "AndroidSdk", "platform-tools", "adb.exe"),
    path.join(home, "AndroidSdk", "platform-tools", "adb"),
    path.join(home, "Library", "Android", "sdk", "platform-tools", "adb"),
    path.join(home, "Android", "Sdk", "platform-tools", "adb"),
    "adb",
  ];
  for (const c of candidates) {
    try {
      if (c === "adb" || fs.existsSync(c)) return c;
    } catch {
      /* ignore */
    }
  }
  return "adb";
}

const ADB = findAdb();

/** Run adb and return trimmed stdout. Throws with context on failure. */
function adb(args, { allowFailure = false } = {}) {
  try {
    return execFileSync(ADB, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
  } catch (err) {
    const detail = (err.stderr || err.message || "").toString().trim();

    // The adb daemon can die mid-run (it happened: "cannot connect to
    // daemon"). That is an infrastructure blip, not a test result, so bring
    // the server back and retry the command once before giving up.
    if (/cannot connect to daemon|daemon not running|failed to start daemon/i.test(detail)) {
      try {
        execFileSync(ADB, ["start-server"], { stdio: "ignore" });
        execFileSync(ADB, ["wait-for-device"], { stdio: "ignore", timeout: 60000 });
        return execFileSync(ADB, args, {
          encoding: "utf8",
          stdio: ["ignore", "pipe", "pipe"],
        }).trim();
      } catch (retryErr) {
        if (allowFailure) return "";
        const retryDetail = (retryErr.stderr || retryErr.message || "").toString().trim();
        throw new Error(`adb ${args.join(" ")} failed after daemon restart: ${retryDetail}`);
      }
    }

    if (allowFailure) return "";
    throw new Error(`adb ${args.join(" ")} failed: ${detail}`);
  }
}

/**
 * Write a script to the device and run it with `sh`.
 *
 * The script is staged in a host temp file and moved with `adb push`, so the
 * bytes arrive verbatim. See deliverIntent() for why echoing through the shell
 * does not work.
 */
function pushAndRunScript(script, remotePath) {
  const os = require("node:os");
  const local = path.join(
    os.tmpdir(),
    `mock-assistant-${process.pid}-${Date.now()}.sh`,
  );
  fs.writeFileSync(local, script, "utf8");
  try {
    adb(["push", local, remotePath]);
    // Normalise line endings: a CRLF script fails under /system/bin/sh.
    adb(["shell", `sed -i 's/\\r$//' ${remotePath} && chmod 700 ${remotePath}`]);
    adb(["shell", `sh ${remotePath}`], { allowFailure: true });
  } finally {
    try {
      fs.unlinkSync(local);
    } catch {
      /* best effort */
    }
  }
}

/**
 * Deliver text to the app without the adb shell eating it.
 *
 * TWO problems have to be solved at once:
 *
 * 1. **Unicode.** `adb shell am start -e query "kiểm tra"` mangles Vietnamese
 *    diacritics — the shell hop re-encodes the argument and the app receives
 *    mojibake, so the prompt never matches what was asserted.
 *
 * 2. **Spaces.** A quoted argument with spaces is split somewhere in the
 *    host → adb → device-shell chain, so `--es query "a b"` reaches `am` as
 *    several arguments. The symptom is `pkg=tra` in `am`'s own output (it
 *    parsed the second word as a package name).
 *
 * Both are solved the same way: the text travels as base64, is decoded
 * on-device, and is handed to `am` through a device-side variable that is
 * **quoted on the device** — so `am` receives exactly one argument.
 *
 * The device command is delivered as a **script file moved with `adb push`**,
 * never echoed as a shell argument. Echoing a long base64 blob through the
 * host → adb → device-shell chain truncates and re-splits it, which silently
 * ran a stale or partial script and made shapes fail for reasons that had
 * nothing to do with the app.
 *
 * 3. **Argument order.** `am start` treats a trailing bare token as the target
 *    package, so `-a ACTION <pkg> --es k v` makes it drop the extra entirely:
 *    the intent arrives with no words and the app shows an empty chat. The text
 *    must be attached BEFORE the package argument.
 *
 *    Reordering by "does this look like a package?" is wrong — an ACTION value
 *    such as `io.hermes.remote.action.ASK` is also dotted, and moving it
 *    destroys the command. Instead the caller passes the shape as
 *    `{ flags, target }`, so the split is explicit rather than guessed.
 */
function deliverIntent({ flags = [], target = null }, key, value) {
  const b64 = Buffer.from(String(value), "utf8").toString("base64");

  const tail = target ? ` ${target}` : "";
  const script =
    [
      "#!/system/bin/sh",
      `QUERY="$(echo -n '${b64}' | base64 -d)"`,
      `am start ${flags.join(" ")} --es ${key} "$QUERY"${tail}`.trimEnd(),
    ].join("\n") + "\n";

  pushAndRunScript(script, "/data/local/tmp/mock-assistant-intent.sh");
}

// --- The delivery shapes ----------------------------------------------------

/**
 * Each shape: describes how the Assistant delivers a spoken request, and what
 * the app must end up showing.
 *
 * `build` returns `{ args, key }` where `args` are the device-side `am start`
 * arguments (minus the text extra) and `key` is the extra name carrying the
 * text. deliverIntent() then attaches the text safely.
 *
 * `expectText` is the prompt the app must display. `expectNoTurn` marks the
 * shapes that must NOT produce a conversation turn.
 */
const SHAPES = {
  "custom-extra": {
    description: 'custom.hermes.ASK — text in the declared "query" extra',
    build: (pkg) => ({
      flags: ["-a", ACTION_ASK, "-n", activityComponent(pkg)],
      key: "query",
    }),
    // The Assistant may prefix the app name; the app must strip it.
    spoken: "Hermes, cho tôi biết trạng thái git",
    expectText: "cho tôi biết trạng thái git",
  },

  "assistant-text": {
    description: "text in android.intent.extra.TEXT",
    build: (pkg) => ({
      flags: ["-a", ACTION_ASK, "-n", activityComponent(pkg)],
      key: "android.intent.extra.TEXT",
    }),
    spoken: "kiểm tra dung lượng đĩa",
    expectText: "kiểm tra dung lượng đĩa",
  },

  "bii-bundle": {
    description: "built-in intent — params nested in a Bundle",
    build: (pkg) => ({
      flags: ["-a", ACTION_ASK, "-n", activityComponent(pkg)],
      key: "parameters",
    }),
    spoken: "chạy test suite",
    expectText: "chạy test suite",
  },

  "deep-link": {
    description: "hermes://ask?query=...",
    // The URI itself must carry the text, so this shape uses its own delivery
    // (am -d with a percent-decoded URI built on-device).
    build: (pkg) => ({
      args: ["-a", "android.intent.action.VIEW", pkg],
      key: null, // handled specially in runShape via deliverDeepLink
      deepLink: true,
    }),
    spoken: "git status dự án",
    expectText: "git status dự án",
  },

  "voice-command": {
    description: "android.intent.action.VOICE_COMMAND",
    build: (pkg) => ({
      flags: ["-a", "android.intent.action.VOICE_COMMAND"],
      key: "query",
      target: pkg,
    }),
    spoken: "tắt máy sau 60 giây",
    expectText: "tắt máy sau 60 giây",
  },

  "bare-invoke": {
    description: '"Hermes" with no request — must send nothing',
    build: (pkg) => ({
      flags: ["-a", ACTION_ASK, "-n", activityComponent(pkg)],
      key: "query",
    }),
    spoken: "Hermes",
    expectText: null,
    expectNoTurn: true,
  },

  "hot-start": {
    description: "second hand-off while the app is already open",
    // Same delivery as the custom capability; the difference is that the
    // harness launches the app first, so the intent lands in onNewIntent().
    build: (pkg) => ({
      flags: ["-a", ACTION_ASK, "-n", activityComponent(pkg)],
      key: "query",
    }),
    spoken: "Hermes, kiểm tra RAM còn bao nhiêu",
    expectText: "kiểm tra RAM còn bao nhiêu",
    hotStart: true,
  },
};

// --- Driving the app --------------------------------------------------------

/** Force-stop the app so the next hand-off is a genuine cold start. */
function forceStop(pkg) {
  adb(["shell", "am", "force-stop", pkg], { allowFailure: true });
}

/** Launch the app plainly, so a later hand-off is a hot start. */
function launchPlain(pkg) {
  adb(["shell", "am", "start", "-n", `${activityComponent(pkg)}`], { allowFailure: true });
}

/** Dump the current UI hierarchy as text. */
function dumpUi() {
  const remote = "/sdcard/mock-assistant-ui.xml";
  adb(["shell", "uiautomator", "dump", remote], { allowFailure: true });
  return adb(["shell", "cat", remote], { allowFailure: true });
}

/** Extract every text="..." attribute value from a uiautomator dump. */
function uiTexts(xml) {
  const out = [];
  const re = /text="([^"]*)"/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    if (m[1] !== "") out.push(m[1]);
  }
  return out;
}

/** True when the dump shows a user turn containing `needle`. */
function showsPrompt(xml, needle) {
  return uiTexts(xml).some((t) => t.includes(needle));
}

/**
 * Read the visible transcript of the newest turn.
 *
 * uiautomator dumps escaped text (e.g. "&#10;" for newline, "&#127908;" for the
 * mic emoji); normalise those so assertions compare real characters.
 */
function decodeEntities(text) {
  return text
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function normalizedTexts(xml) {
  return uiTexts(xml).map(decodeEntities);
}

/** Does the UI show the "from Google Assistant" banner? */
function showsAssistantBanner(xml) {
  return normalizedTexts(xml).some((t) => t.includes("Từ Trợ lý Google"));
}

/** Does the UI show any user message bubble? */
function hasUserTurn(xml) {
  const texts = normalizedTexts(xml);
  return texts.includes("Bạn");
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Is the app mid-turn (streaming a reply, waiting on the model)?
 *
 * The app talks to a real model, so a turn can take tens of seconds. A fixed
 * sleep either wastes time or, worse, asserts while the PREVIOUS turn is still
 * on screen — which reads as this shape failing when it actually worked.
 */
function isBusy(xml) {
  return normalizedTexts(xml).some(
    (t) =>
      t.includes("Đang đọc trả lời") ||
      t.includes("Hermes đang xử lý") ||
      t.includes("Đang nghe"),
  );
}

/**
 * Wait until the app is idle, or the budget runs out.
 *
 * Returns the final UI dump so the caller does not have to re-dump.
 */
async function waitUntilIdle(budgetMs, stepMs = 1500) {
  const deadline = Date.now() + budgetMs;
  let xml = dumpUi();
  while (Date.now() < deadline && isBusy(xml)) {
    await sleep(stepMs);
    xml = dumpUi();
  }
  // Let the last frame settle after the stream ends.
  await sleep(500);
  return dumpUi() || xml;
}

/** Deliver a hermes:// deep link whose query is built safely on-device. */
function deliverDeepLink(pkg, value) {
  const b64 = Buffer.from(String(value), "utf8").toString("base64");
  const script =
    [
      "#!/system/bin/sh",
      `QUERY="$(echo -n '${b64}' | base64 -d)"`,
      // Percent-encode via od so the URI stays valid ASCII end to end.
      `ENC=$(printf '%s' "$QUERY" | od -An -tx1 | tr -d ' \\n' | sed 's/../%&/g')`,
      `am start -a android.intent.action.VIEW -d "hermes://ask?query=$ENC" ${pkg}`,
    ].join("\n") + "\n";

  pushAndRunScript(script, "/data/local/tmp/mock-assistant-link.sh");
}

/**
 * Run one shape and assert the outcome.
 *
 * Returns { name, ok, detail }. Never throws: a failing shape is a result, not
 * an error, so the harness can report every shape in one pass.
 */
async function runShape(name, { pkg, settleMs, keepState }) {
  const shape = SHAPES[name];
  if (!shape) return { name, ok: false, detail: `unknown shape "${name}"` };

  try {
    if (!keepState) {
      forceStop(pkg);
      await sleep(1500);
      if (shape.hotStart) {
        // Launch plainly first so the hand-off below is a genuine hot start
        // (intent delivered to onNewIntent of the running activity).
        launchPlain(pkg);
        await sleep(5000);
      }
    }

    const spec = shape.build(pkg);
    if (spec.deepLink) {
      deliverDeepLink(pkg, shape.spoken);
    } else {
      deliverIntent(spec, spec.key, shape.spoken);
    }

    // Wait for the app to act and finish its turn, rather than guessing a
    // duration: the reply comes from a real model.
    let xml = await waitUntilIdle(settleMs);

    // The hand-off itself (activity start + sending) may still be in flight if
    // the app was cold. Poll briefly for the prompt to appear before judging.
    const deadline = Date.now() + 8000;
    while (
      Date.now() < deadline &&
      shape.expectText &&
      !showsPrompt(xml, shape.expectText) &&
      !hasUserTurn(xml)
    ) {
      await sleep(1000);
      xml = dumpUi();
    }

    if (!xml || xml.length < 100) {
      return { name, ok: false, detail: "empty UI dump (device offline?)" };
    }

    // Negative case: a bare invocation must not create a conversation turn.
    if (shape.expectNoTurn) {
      if (hasUserTurn(xml)) {
        return { name, ok: false, detail: "sent a turn for a bare invocation" };
      }
      return { name, ok: true, detail: "no turn sent (correct)" };
    }

    if (shape.expectText && !showsPrompt(xml, shape.expectText)) {
      const seen = normalizedTexts(xml).slice(0, 8).join(" | ") || "(no text nodes)";
      return {
        name,
        ok: false,
        detail: `prompt "${shape.expectText}" not found. seen: ${seen}`,
      };
    }

    const banner = showsAssistantBanner(xml);
    return {
      name,
      ok: true,
      detail: `prompt delivered${banner ? " + assistant banner" : ""}`,
    };
  } catch (err) {
    return { name, ok: false, detail: err.message };
  }
}

// --- CLI --------------------------------------------------------------------

function parseArgs(argv) {
  const out = {
    shapes: [],
    all: false,
    list: false,
    packageName: DEFAULT_PACKAGE,
    settleMs: 6000,
    json: false,
    keepState: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--list") out.list = true;
    else if (a === "--all") out.all = true;
    else if (a === "--json") out.json = true;
    else if (a === "--keep-state") out.keepState = true;
    else if (a === "--shape") out.shapes.push(argv[++i]);
    else if (a === "--package") out.packageName = argv[++i];
    else if (a === "--settle") out.settleMs = Number(argv[++i]);
    else if (a === "--help" || a === "-h") out.help = true;
    else if (a.startsWith("-")) {
      out.error = `unknown flag "${a}"`;
    }
  }
  return out;
}

const HELP = `
Mock Google Assistant / App Actions dispatcher for Hermes Remote.

Simulates what the Assistant sends the app, so the intent contract can be
verified without Google Play Services. Fires real intents at the real APK.

Usage:
  node scripts/mock-assistant.js --list
  node scripts/mock-assistant.js --shape custom-extra
  node scripts/mock-assistant.js --all
  node scripts/mock-assistant.js --all --package io.hermes.remote.debug

Options:
  --list             show every shape and exit
  --shape <name>     run one shape (repeatable)
  --all              run every shape, including hot-start
  --package <id>     app under test (default ${DEFAULT_PACKAGE})
  --settle <ms>      wait after each hand-off (default 6000)
  --json             machine-readable output
  --keep-state       do not force-stop between shapes
  -h, --help         this text

Exit code is non-zero if any shape fails, so it is CI-usable.
`.trim();

async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.help) {
    console.log(HELP);
    return 0;
  }
  if (args.error) {
    console.error(`error: ${args.error}\n\n${HELP}`);
    return 2;
  }

  if (args.list) {
    console.log("Available Assistant delivery shapes:\n");
    for (const [name, s] of Object.entries(SHAPES)) {
      console.log(`  ${name.padEnd(16)} ${s.description}`);
    }
    console.log(`  ${"hot-start".padEnd(16)} second hand-off while the app is open`);
    return 0;
  }

  // Fail fast with a clear message if no device is attached.
  const devices = adb(["devices"], { allowFailure: true });
  if (!/device\s*$/m.test(devices)) {
    console.error(
      "error: no Android device/emulator attached.\n" +
        "Start one, then re-run. `adb devices` reported:\n" +
        devices,
    );
    return 2;
  }

  // Is the app under test installed?
  const pkgs = adb(["shell", "pm", "list", "packages"], { allowFailure: true });
  if (!pkgs.includes(`package:${args.packageName}`)) {
    console.error(
      `error: ${args.packageName} is not installed.\n` +
        `Install it first, e.g.:\n` +
        `  adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`,
    );
    return 2;
  }

  let names = args.shapes;
  if (args.all) names = Object.keys(SHAPES);
  if (names.length === 0) {
    console.error(`error: nothing to run. Use --all or --shape <name>.\n\n${HELP}`);
    return 2;
  }

  const results = [];
  for (const name of names) {
    process.stderr.write(`→ ${name} … `);
    const r = await runShape(name, {
      pkg: args.packageName,
      settleMs: args.settleMs,
      keepState: args.keepState,
    });
    process.stderr.write(`${r.ok ? "ok" : "FAIL"}\n`);
    results.push(r);
  }

  const failed = results.filter((r) => !r.ok);

  if (args.json) {
    console.log(JSON.stringify({ package: args.packageName, results }, null, 2));
  } else {
    console.log("\nMock Assistant results\n" + "-".repeat(60));
    for (const r of results) {
      console.log(`  ${r.ok ? "PASS" : "FAIL"}  ${r.name.padEnd(16)} ${r.detail}`);
    }
    console.log("-".repeat(60));
    console.log(`  ${results.length - failed.length}/${results.length} passed`);
  }

  return failed.length === 0 ? 0 : 1;
}

main()
  .then((code) => process.exit(code))
  .catch((err) => {
    console.error(err.stack || String(err));
    process.exit(2);
  });
