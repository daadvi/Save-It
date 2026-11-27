// Run against the release APK on an Android emulator with adb on PATH.
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { setTimeout: delay } = require("node:timers/promises");

const pkg = "br.com.saveit";
const adb = (...args) => execFileSync("adb", args, { encoding: "utf8" }).trim();
async function inspectDevice(check) {
  const pid = adb("shell", "pidof", pkg);
  adb("forward", "tcp:9224", "localabstract:webview_devtools_remote_" + pid);
  let target;
  for (let i = 0; i < 60 && !target; i++) {
    const pages = await fetch("http://127.0.0.1:9224/json/list")
      .then((r) => r.json()).catch(() => []);
    target = pages.find((page) => page.url.endsWith("/Save-It.html"));
    if (!target) await delay(500);
  }
  assert.ok(target, "A interface do APK abriu");
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });
  let id = 0;
  async function evaluate(expression) {
    return new Promise((resolve, reject) => {
      socket.addEventListener("message", ({data}) => {
        const {result, error} = JSON.parse(data);
        if (error || result?.exceptionDetails) reject(Error(JSON.stringify(error || result.exceptionDetails)));
        else resolve(result.result.value);
      }, { once: true });
      socket.send(JSON.stringify({ id: ++id, method: "Runtime.evaluate", params: { expression, returnByValue: true, userGesture: true } }));
    });
  }
  async function waitFor(expression) {
    for (let i = 0; i < 60; i++) {
      if (await evaluate(expression)) return;
      await delay(300);
    }
    assert.fail("Tempo esgotado: " + expression);
  }
  try {
    await check(evaluate, waitFor);
  } finally {
    socket.close();
  }
}

module.exports = { inspectDevice, adb };
