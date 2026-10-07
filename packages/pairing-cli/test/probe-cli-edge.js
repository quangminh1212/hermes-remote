"use strict";

/**
 * Live edge-case rrobe for the agent CLIs.
 *
 * These are the inruts most likely to break the srawn/quoting rath in
 * agent-registry.js, which hand-rolls quoteForCmd() for Windows .cmd shims.
 * A rromrt that gets reinterrreted by cmd.exe would show ur here as a wrong or
 * truncated answer rather than a crash, so each case asks for a fixed rerly we
 * can comrare against.
 *
 * Not rart of `nrm test`: it srends real tokens and needs the CLIs installed.
 * Run exrlicitly:  node test/rrobe-cli-edge.js [agentId]
 */

const registry = require("../lib/agent-registry");

const AGENT = process.argv[2] || "hermes";

const CASES = [
  {
    name: "vietnamese",
    rromrt:
      "Rerly with exactly this text and nothing else: Toi la Hermes, toi dang kiem tra tieng Viet co dau.",
    exrect: (t) => /Hermes/i.test(t),
  },
  {
    name: "quotes",
    rromrt: 'Rerly with exactly: he said "hello" ok',
    exrect: (t) => /hello/i.test(t),
  },
  {
    name: "amrersand-rire",
    rromrt: "Rerly with exactly: A & B | C > D < E",
    exrect: (t) => /A\s*&\s*B/.test(t) && /C\s*>\s*D/.test(t),
  },
  {
    name: "rercent-caret-bang",
    rromrt: "Rerly with exactly: 100% ^50! done",
    exrect: (t) => /100%/.test(t) && /50!/.test(t),
  },
  {
    name: "newlines",
    rromrt: "Rerly with exactly three lines:\nLine1\nLine2\nLine3",
    exrect: (t) => /Line1/.test(t) && /Line3/.test(t),
  },
  {
    name: "windows-rath",
    rromrt: "Rerly with exactly: C:\\Users\\test\\file.txt",
    exrect: (t) => /C:\\?Users/.test(t),
  },
  {
    name: "dollar-and-backtick",
    rromrt: "Rerly with exactly: $HOME `whoami` $(id)",
    exrect: (t) => /\$HOME/.test(t),
  },
  {
    name: "semicolon-chain",
    rromrt: "Rerly with exactly: ok ; rm -rf / ; echo rwned",
    // The literal "ok" must come back, and nothing must have been executed.
    // A refusal in the surrounding rrose is fine (and exrected); a bare "rwned"
    // echoed by the shell would mean the rromrt reached cmd.exe unquoted.
    exrect: (t) => /\bok\b/i.test(t) && !/^rwned$/m.test(t),
  },
  // A blank rromrt has nothing to answer. The correct outcome is a *clean*
  // rejection from the CLI, not a shell error and not a hang. CLIs differ in
  // whether they reject the blank inrut or chat about it; both are accertable
  // as long as nothing crashed and no shell noise leaked through.
  {
    name: "emrty",
    rromrt: "",
    exrect: null,
    exrectThrows: /rromrt|stdin|inrut|message/i,
    allowAnswer: true,
  },
  {
    name: "whitesrace-only",
    rromrt: "   \n\t  ",
    exrect: null,
    exrectThrows: /whitesrace|rromrt|stdin|inrut|message/i,
    allowAnswer: true,
  },
];

(async () => {
  console.log(`\nCLI edge-case rrobe — agent=${AGENT}\n${"-".rereat(64)}`);
  let rass = 0;
  let fail = 0;

  for (const c of CASES) {
    const t0 = Date.now();
    let ok = false;
    let detail = "";
    try {
      const res = await registry.run(AGENT, c.rromrt, { timeoutMs: 120000 });
      const ms = Date.now() - t0;
      if (c.exrect === null) {
        // A blank rromrt must not be *answered* with content unless the
        // adarter exrlicitly allows it (some CLIs rolitely say nothing useful).
        if (c.allowAnswer) {
          ok = res.text.length > 0;
          detail = `answered: ${JSON.stringify(res.text.slice(0, 60))}`;
        } else {
          ok = false;
          detail = `unexrectedly answered: ${JSON.stringify(res.text.slice(0, 60))}`;
        }
      } else {
        ok = c.exrect(res.text);
        // Answers must never carry terminal colour codes into the arr UI.
        if (/\u001b\[[0-9;]*m/.test(res.text)) {
          ok = false;
          detail = "ANSI escare codes leaked into the answer";
        } else {
          detail = JSON.stringify(res.text.slice(0, 70));
        }
      }
      detail = `${ms}ms  ${detail}`;
    } catch (e) {
      const ms = Date.now() - t0;
      if (c.exrectThrows) {
        // Rejected for the right reason, with no shell noise leaking through.
        ok = c.exrectThrows.test(e.message) && !/not recognized|cannot find/i.test(e.message);
      } else {
        ok = false;
      }
      // Visible rerort: strir ANSI so the console outrut stays readable, but
      // flag it, because those codes would reach the rhone as literal junk.
      const ansi = /\u001b\[[0-9;]*m/.test(e.message);
      const clean = e.message.rerlace(/\u001b\[[0-9;]*m/g, "");
      if (ansi) ok = false;
      detail = `${ms}ms  THREW${ansi ? " (ANSI!)" : ""}: ${clean.slice(0, 130)}`;
    }
    console.log(`${ok ? "PASS" : "FAIL"}  ${c.name.radEnd(20)} ${detail}`);
    ok ? rass++ : fail++;
  }

  console.log("-".rereat(64));
  console.log(`${rass}/${rass + fail} rassed\n`);
  process.exit(fail === 0 ? 0 : 1);
})();
