"use strict";

/**
 * Tests for the pairing page.
 *
 * The page is the first thing a user sees, and it renders user-influenced
 * values (the server URL, the model name) into HTML. Both the design-system
 * wiring and the escaping are asserted here.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const { renderPage, esc, TOKENS } = require("../lib/pairing-page");

const SAMPLE = {
  baseUrl: "https://abc.trycloudflare.com",
  fallbackUrl: "http://192.168.1.10:8642",
  model: "glm-5.3",
  profile: "",
};

test("renders a complete, well-formed document", () => {
  const html = renderPage(SAMPLE);
  assert.match(html, /^<!doctype html>/i);
  assert.match(html, /<\/html>\s*$/);
  assert.match(html, /<meta charset="utf-8">/);
  assert.match(html, /<meta name="viewport"/);
  // English is the default locale when no --lang is given.
  assert.match(html, /lang="en"/);
});

test("shows the QR image and the server details", () => {
  const html = renderPage(SAMPLE);
  assert.match(html, /src="\/qr\.png"/);
  assert.ok(html.includes(SAMPLE.baseUrl));
  assert.ok(html.includes(SAMPLE.fallbackUrl));
  assert.ok(html.includes(SAMPLE.model));
});

test("falls back to a default label when model and profile are missing", () => {
  const html = renderPage({ baseUrl: "http://x", fallbackUrl: "http://y" });
  assert.match(html, /\(default\)/);
});

test("shows an explicit profile when one is set", () => {
  const html = renderPage({ ...SAMPLE, profile: "dalek" });
  assert.ok(html.includes("dalek"));
  assert.ok(!html.includes("(default)"));
});

test("escapes user-influenced values in text and attributes", () => {
  const html = renderPage({
    ...SAMPLE,
    baseUrl: 'https://x/"><script>alert(1)</script>',
    model: "a & b",
  });
  assert.ok(!html.includes("<script>alert(1)</script>"), "raw script tag leaked");
  assert.ok(html.includes("&quot;&gt;&lt;script&gt;"), "quotes/angles not escaped");
  assert.ok(html.includes("a &amp; b"), "ampersand not escaped");
});

test("esc() covers every HTML-significant character", () => {
  assert.equal(esc(`&<>"'`), "&amp;&lt;&gt;&quot;&#39;");
  assert.equal(esc(42), "42");
  assert.equal(esc(null), "null");
});

test("carries the XLab brand colour, not an ad-hoc palette", () => {
  const html = renderPage(SAMPLE);
  // Brand teal must appear; it is the one token shared with the app and web.
  assert.ok(
    html.toUpperCase().includes(TOKENS.primary.toUpperCase()),
    "brand colour missing from the rendered page",
  );
  assert.ok(html.includes("--primary"));
});

test("tokens match the XLab design system values", () => {
  // These are the contract with tailwind.config.js and XLabTheme.kt. If the
  // brand moves, all three must move together.
  assert.equal(TOKENS.primary, "#00A19A");
  assert.equal(TOKENS.bgPrimary, "#1E293B");
  assert.equal(TOKENS.bgSecondary, "#334155");
  assert.equal(TOKENS.textPrimary, "#F7F9FB");
});

test("is dark-only, matching the app", () => {
  const html = renderPage(SAMPLE);
  assert.match(html, /color-scheme:\s*dark/);
});
