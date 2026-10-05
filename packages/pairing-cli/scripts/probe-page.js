"use strict";
const { renderPage } = require("C:/Dev/Hermes_Android/packages/pairing-cli/lib/pairing-page");

const base = {
  baseUrl: "https://x.trycloudflare.com",
  fallbackUrl: "http://192.168.1.5:8642",
  model: "Claude-Fable.3",
  profile: "",
  agents: ["claude", "codex", "opencode"],
};

for (const lang of ["en", "vi", "zh"]) {
  const html = renderPage(Object.assign({ lang }, base));
  const pick = (re) => (html.match(re) || [, "(none)"])[1];
  console.log(lang + ":");
  console.log("  htmlLang = " + pick(/<html lang="([^"]*)"/));
  console.log("  h1       = " + pick(/<h1>([^<]*)<\/h1>/));
  console.log("  title    = " + pick(/<title>([^<]*)<\/title>/));
  console.log("  agents   = " + (html.includes("claude · codex · opencode") ? "OK" : "MISSING"));
  console.log("  default  = " + (html.includes("\u4e2d\u6587默认") ? "zh" : "?"));
}

// Khong truyen lang -> phai la tieng Anh (mac dinh).
const noLang = renderPage(base);
console.log("\nno --lang -> h1 = " + (noLang.match(/<h1>([^<]*)<\/h1>/) || [, "?"])[1]);

// Khong co agent -> khong hien dong agents.
const noAgents = renderPage({ lang: "en", baseUrl: "http://a", fallbackUrl: "http://b", model: "m" });
console.log("no agents -> agent row hidden = " + (!noAgents.includes("Ask the app to use any of these")));
