"use strict";

/**
 * Tests for answering in the asker's language.
 *
 * The bug this prevents: a Chinese question comes back in English because the
 * agent CLI has no idea what language the user wrote in. The bridge now
 * prepends an explicit instruction.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const { detectScript, languageDirective } = require("../lib/i18n");
const { lastUserMessage } = require("../lib/agent-bridge");

test("detects Chinese from Han characters", () => {
  assert.equal(detectScript("请帮我检查一下内存"), "zh");
  assert.equal(detectScript("今天天气怎么样？"), "zh");
  assert.equal(detectScript("运行测试 suite 并总结结果"), "zh");
});

test("detects Vietnamese from its own diacritics", () => {
  assert.equal(detectScript("kiểm tra RAM giúp tôi"), "vi");
  assert.equal(detectScript("mở thư mục hiện tại"), "vi");
  assert.equal(detectScript("chạy test đi"), "vi");
});

test("does not claim a language for English or ambiguous Latin text", () => {
  // A wrong guess is worse than none: it would force the wrong language.
  assert.equal(detectScript("check the RAM for me"), null);
  assert.equal(detectScript("list files in the current folder"), null);
  assert.equal(detectScript("git status"), null);
  assert.equal(detectScript(""), null);
  assert.equal(detectScript(null), null);
  // Plain ASCII is not evidence of English.
  assert.equal(detectScript("cafe"), null);
});

test("names the detected language explicitly", () => {
  const vi = languageDirective("kiểm tra RAM giúp tôi");
  assert.match(vi, /Vietnamese/);
  assert.match(vi, /\[Language\]/);

  const zh = languageDirective("请检查内存");
  assert.match(zh, /Simplified Chinese/);
});

test("falls back to mirroring the user when detection is unsure", () => {
  const d = languageDirective("check the RAM");
  assert.match(d, /same language the user wrote in/);
  // Must not name a language it could not prove.
  assert.doesNotMatch(d, /Vietnamese|Chinese|English only/);
});

test("never asks the agent to translate code or commands", () => {
  // Localising a command name would break it.
  for (const q of ["请检查内存", "kiểm tra RAM", "check the RAM"]) {
    assert.match(languageDirective(q), /code, commands, file paths and error messages/);
  }
});

test("attaches the directive as a prefix, leaving the question intact", () => {
  const prompt = languageDirective("kiểm tra ổ đĩa") + "\n\n" + "kiểm tra ổ đĩa";
  assert.ok(prompt.startsWith("[Language]"));
  assert.ok(prompt.trim().endsWith("kiểm tra ổ đĩa"));
});

test("lastUserMessage picks the final user turn, not the assistant's", () => {
  const messages = [
    { role: "user", content: "hello" },
    { role: "assistant", content: "hi there" },
    { role: "user", content: "请检查内存" },
  ];
  assert.equal(lastUserMessage(messages), "请检查内存");
});

test("lastUserMessage reads array-shaped content", () => {
  const messages = [
    { role: "user", content: [{ type: "text", text: "kiểm tra" }, { type: "text", text: " RAM" }] },
  ];
  assert.equal(lastUserMessage(messages), "kiểm tra RAM");
});

test("lastUserMessage returns empty for junk input", () => {
  assert.equal(lastUserMessage([]), "");
  assert.equal(lastUserMessage(null), "");
  assert.equal(lastUserMessage(undefined), "");
  assert.equal(lastUserMessage([{ role: "assistant", content: "only assistant" }]), "");
  assert.equal(lastUserMessage([{ role: "user", content: "   " }]), "");
});
