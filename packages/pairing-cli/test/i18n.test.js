"use strict";

/**
 * Tests for the CLI/page translations.
 *
 * The failure this guards against is a *gap*: a language that silently falls
 * back to English everywhere looks translated but isn't, and a missing key
 * shows up as a raw key name in the console. Both are caught here.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const { LANGS, MESSAGES, resolveLang, translator } = require("../lib/i18n");

test("supports exactly the documented languages", () => {
  assert.deepEqual(LANGS, ["en", "vi", "zh"]);
  for (const l of LANGS) assert.ok(MESSAGES[l], `missing table for ${l}`);
});

test("every language defines every English key", () => {
  const english = Object.keys(MESSAGES.en).sort();
  for (const lang of LANGS) {
    if (lang === "en") continue;
    const keys = Object.keys(MESSAGES[lang]).sort();
    const missing = english.filter((k) => !keys.includes(k));
    const extra = keys.filter((k) => !english.includes(k));
    assert.deepEqual(missing, [], `${lang} is missing keys`);
    assert.deepEqual(extra, [], `${lang} has keys English lacks`);
  }
});

test("no language leaves a translation empty", () => {
  for (const lang of LANGS) {
    for (const [k, v] of Object.entries(MESSAGES[lang])) {
      assert.equal(typeof v, "string", `${lang}.${k} is not a string`);
      assert.ok(v.trim().length > 0, `${lang}.${k} is empty`);
    }
  }
});

test("placeholders survive translation", () => {
  // A "%s" lost in translation means an argument silently disappears.
  for (const lang of LANGS) {
    for (const [k, v] of Object.entries(MESSAGES[lang])) {
      if (MESSAGES.en[k] === undefined) continue;
      const enCount = (MESSAGES.en[k].match(/%s/g) || []).length;
      const thisCount = (String(v).match(/%s/g) || []).length;
      assert.equal(thisCount, enCount, `${lang}.${k} placeholder count differs`);
    }
  }
});

test("translator substitutes arguments", () => {
  assert.equal(translator("en")("lan_address", "http://10.0.0.2:8642"), "LAN address: http://10.0.0.2:8642");
  assert.equal(translator("vi")("model_label", "grok"), "Model             : grok");
  assert.ok(translator("zh")("agents_found", "claude").includes("claude"));
});

test("translator falls back to English for an unknown key", () => {
  const t = translator("zh");
  // Not a real key anywhere.
  assert.equal(t("definitely_not_a_key"), "definitely_not_a_key");
});

test("resolveLang normalises real-world locale tags", () => {
  assert.equal(resolveLang("vi"), "vi");
  assert.equal(resolveLang("VI-vn"), "vi");
  assert.equal(resolveLang("zh-CN"), "zh");
  assert.equal(resolveLang("zh_Hans"), "zh");
  assert.equal(resolveLang("en-US"), "en");
  assert.equal(resolveLang(""), "en");
  assert.equal(resolveLang(undefined), "en");
});

test("resolveLang maps an unsupported locale to English", () => {
  // Better a language the user may read than a half-translated page.
  for (const l of ["fr", "de", "ja", "ko", "ru", "ar"]) {
    assert.equal(resolveLang(l), "en", `${l} should fall back to English`);
  }
});

test("HERMES_LANG is honoured when no flag is given", () => {
  const before = process.env.HERMES_LANG;
  try {
    process.env.HERMES_LANG = "zh-TW";
    assert.equal(resolveLang(), "zh");
    // An explicit flag still wins.
    assert.equal(resolveLang("vi"), "vi");
  } finally {
    if (before === undefined) delete process.env.HERMES_LANG;
    else process.env.HERMES_LANG = before;
  }
});
