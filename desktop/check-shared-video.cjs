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
    await evaluate('document.querySelector("[data-nav=folders]").click()');
    const filterGap = await evaluate(`document.querySelector('[data-status="pending"]').getBoundingClientRect().top
      - document.querySelector(".folder-grid + .empty").getBoundingClientRect().bottom`);
    assert.ok(Math.abs(filterGap - 16) < 1, "Espaço entre o quadro e os filtros de status: " + filterGap);
    for (const status of ["done", "all", "pending"]) {
      await evaluate(`document.querySelector('[data-status="${status}"]').click()`);
      assert.equal(await evaluate('document.querySelector("[data-status].active").dataset.status'), status);
    }
    const gap = await evaluate('innerHeight - document.querySelector(".dock").getBoundingClientRect().bottom');
    assert.ok(Math.abs(gap - 6) < 1, "Margem inferior da dock: " + gap);
    // A CSS-only check misses extra space between the WebView and the system bar.
    const display = adb("shell", "dumpsys", "window", "displays");
    const navbar = display.split("\n").find(line =>
      /(?:type=|mType=|ITYPE_)navigation_?bars?\b/i.test(line) && /(?:mFrame|frame)=\[0,\d+\]\[/.test(line));
    assert.ok(navbar, "Área da navegação do sistema disponível");
    const navTop = Number(navbar.match(/(?:mFrame|frame)=\[0,(\d+)\]/)[1]);
    const scale = await evaluate('devicePixelRatio');
    await evaluate('document.querySelector(".dock").style.borderBottomColor = "rgb(250, 0, 230)"');
    await delay(350);
    const frame = execFileSync("adb", ["exec-out", "screencap"], {maxBuffer: 64 * 1024 * 1024});
    const width = frame.readUInt32LE(0), height = frame.readUInt32LE(4);
    const header = frame.length - width * height * 4;
    assert.ok(header >= 12 && header <= 32, "Captura RGBA do emulador");
    const x = Math.floor(width / 2);
    let borderBottom = 0;
    for (let y = 0; y < height; y++) {
      const i = header + (y * width + x) * 4;
      if (frame[i] > 235 && frame[i + 1] < 15 && frame[i + 2] > 215) borderBottom = y + 1;
    }
    assert.ok(borderBottom > 0, "Borda inferior visível na captura");
    const physicalGap = (navTop - borderBottom) / scale;
    console.log("Distância entre a dock e a barra do sistema em dp:", physicalGap);
    assert.ok(Math.abs(physicalGap - 6) < 1.5, "Área segura contada uma vez");
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
