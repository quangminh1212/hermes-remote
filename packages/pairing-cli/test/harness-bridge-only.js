/**
 * Start the pairing web server + agent bridge WITHOUT the Hermes gateway.
 *
 * The normal entry point (`bin/cli.js`) refuses to run unless the gateway on
 * :8642 answers, because on a real install the bridge proxies unknown agents
 * through it. For UI verification on a machine whose `.env` has no provider
 * keys, that dependency is pure friction: the bridge serves the CLIs directly
 * and the phone never talks to the gateway at all.
 *
 * This is a test harness, not a product entry point.
 *
 *   node test/harness-bridge-only.js [--port 8899] [--agent hermes]
 */
const crypto = require("crypto");
const os = require("os");
const { createServer, listen } = require("../lib/server");
const { listenBridge } = require("../lib/agent-bridge");
const registry = require("../lib/agent-registry");

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i !== -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

(async () => {
  const webPort = parseInt(arg("port", "8899"), 10);
  const agent = arg("agent", "hermes");
  const bridgePort = parseInt(arg("bridgePort", "8643"), 10);

  const apiKey = crypto.randomBytes(32).toString("hex");

  // The phone is a different device, so the payload must carry the LAN address
  // rather than loopback. os.networkInterfaces() picks the same address the CLI
  // prints in its banner.
  const lan =
    Object.values(os.networkInterfaces())
      .flat()
      .find((i) => i && i.family === "IPv4" && !i.internal)?.address ||
    "127.0.0.1";

  const bridge = await listenBridge(
    {
      defaultAgent: agent,
      timeoutMs: 300000,
      apiKey,
    },
    bridgePort,
    "0.0.0.0",
  );

  const agentIds = registry.available().map((a) => a.id);

  const payload = {
    v: 1,
    baseUrl: `http://${lan}:${bridgePort}`,
    fallbackUrl: `http://${lan}:${bridgePort}`,
    apiKey,
    profile: "",
    model: "glm-5.3", // deliberately NOT a CLI id — exercises the app's retarget
    profiles: [],
    agents: agentIds,
    defaultAgent: agent,
    lang: "en",
  };

  const app = createServer({ payload, agents: agentIds, lang: "en" });
  const { port } = await listen(app, webPort);

  console.log(`web     http://127.0.0.1:${port}/api/connect`);
  console.log(`bridge  http://127.0.0.1:${bridgePort}/health`);
  console.log(`agents  ${agentIds.join(", ")}`);
  console.log(`payload ${JSON.stringify(payload)}`);

  process.on("SIGTERM", () => {
    bridge.close?.();
    process.exit(0);
  });
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
