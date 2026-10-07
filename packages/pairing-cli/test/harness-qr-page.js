/**
 * Start the pairing web page WITHOUT the gateway and WITHOUT the agent bridge.
 *
 * `bin/cli.js` refuses to run unless the gateway on :8642 answers, and
 * `harness-bridge-only.js` still opens the bridge on a second port. Neither is
 * what you want when the only thing under test is the QR page itself: this
 * serves `createServer` alone and prints the payload the phone would receive,
 * so a layout or payload change can be checked without a gateway, a bridge or
 * a phone.
 *
 * This is a test harness, not a product entry point.
 *
 *   node test/harness-qr-page.js [--port 8660] [--lang vi] [--profile ""]
 *
 * Then read the payload the phone would get:
 *   curl http://127.0.0.1:8660/api/connect
 */
const { createServer, listen } = require("../lib/server");
const config = require("../lib/config");
const registry = require("../lib/agent-registry");
const { routeAgents, lanIP } = require("../bin/cli");

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i !== -1 && process.argv[i + 1] !== undefined ? process.argv[i + 1] : fallback;
}

const cfg = config.readHermesConfig();
const port = parseInt(arg("port", "8660"), 10);
const lang = arg("lang", "vi");
const profile = arg("profile", "");
const route = routeAgents(registry.available());
const lan = `http://${lanIP()}:${cfg.port}`;
// With no spawnable adapter the phone must talk to the gateway directly.
const chatUrl = route.useBridge ? `http://${lanIP()}:${cfg.port + 1}` : lan;

const payload = {
  v: 1,
  baseUrl: chatUrl,
  fallbackUrl: lan,
  apiKey: cfg.apiKey,
  profile,
  model: cfg.model,
  profiles: config.listProfiles(cfg.envFile),
  agents: route.agents,
  defaultAgent: "",
  lang,
};

const app = createServer({
  payload,
  publicUrl: lan,
  localUrl: lan,
  apiKey: cfg.apiKey,
  model: cfg.model,
  profiles: payload.profiles,
  profile,
  gatewayUrl: lan,
  lang,
  agents: route.agents,
});

listen(app, port).then(({ url }) => {
  console.log("PAIRING_PAGE=" + url);
  console.log("CHAT_URL=" + chatUrl);
  console.log("AGENTS=" + JSON.stringify(route.agents));
  console.log("USE_BRIDGE=" + route.useBridge);
  console.log("NOTICE=" + route.notice);
});
