// Run against the release APK on an Android emulator with adb on PATH.
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { setTimeout: delay } = require("node:timers/promises");

const pkg = "br.com.saveit";
const adb = (...args) => execFileSync("adb", args, { encoding: "utf8" }).trim();
const reel = "https://www.instagram.com/reel/SaveItTest/";
const video = "https://example.org/assistir/2";

function share(mime, extra, url) {
  // Resolve the implicit intent against the app's registered share filters.
  const output = adb("shell", "am", "start", "-W", "-a", "android.intent.action.SEND",
    "-p", pkg, "-t", mime, extra === "STREAM" ? "--eu" : "--es", "android.intent.extra." + extra, url);
  assert.match(output, /Status: ok/);
}

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
      socket.send(JSON.stringify({ id: ++id, method: "Runtime.evaluate", params: { expression, returnByValue: true } }));
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

(async () => {
  share("text/plain", "TEXT", reel);
  const hasCard = (url) => `[...document.querySelectorAll("article a.cover")].some(el => el.href === ${JSON.stringify(url)})`;
  await inspectDevice(async (evaluate, waitFor) => {
    await waitFor(hasCard(reel));
    const gap = await evaluate('innerHeight - document.querySelector(".dock").getBoundingClientRect().bottom');
    assert.ok(Math.abs(gap - 8) < 1, "Margem inferior da dock: " + gap);
    await evaluate('document.querySelector("[data-nav=settings]").click()');
    share("video/mp4", "STREAM", video);
    await waitFor(hasCard(video));
    share("text/plain", "TEXT", reel);
    await waitFor('document.querySelector("#toast").textContent.includes("já está na sua coleção")');
    await evaluate('document.querySelector("[data-type=video]").click()');
    assert.deepEqual(await evaluate('[...document.querySelectorAll("article .type-badge")].map(el => el.textContent)'), ["Vídeo", "Vídeo"]);
  });

  adb("shell", "am", "force-stop", pkg);
  adb("shell", "am", "start", "-W", "-n", pkg + "/.MainActivity");
  await inspectDevice(async (evaluate, waitFor) => {
    await waitFor(hasCard(reel));
    assert.equal(await evaluate('document.querySelectorAll("article").length'), 2);
  });
  console.log("Reel e vídeo salvos via ACTION_SEND, classificados no mural e preservados após reiniciar.");
})().catch((error) => { console.error(error); process.exitCode = 1; });
