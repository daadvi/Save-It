const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");

async function connect(port) {
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const target = pages.find((entry) => entry.url.includes("Save-It.html"));
  assert.ok(target, `HTML target not available on ${port}`);
  console.log(`Conectando a ${target.url} na porta ${port}`);
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });
  socket.addEventListener("close", () => { for (const call of queue.values()) call.reject(new Error("CDP closed")); });
  let id = 0;
  const queue = new Map();
  socket.addEventListener("message", (event) => {
    const { id, result, error } = JSON.parse(event.data);
    const call = queue.get(id);
    if (!call) return;
    queue.delete(id);
    error ? call.reject(error) : call.resolve(result);
  });
  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      queue.set(++id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }
  return {
    async evaluate(expression) {
      const { result, exceptionDetails } = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
      assert.ok(!exceptionDetails, JSON.stringify(exceptionDetails));
      return result.value;
    },
    close() { socket.close(); },
  };
}

(async () => {
  const deadline = setTimeout(() => { console.error("CDP timed out"); process.exit(1); }, 45000);
  execFileSync("adb", ["shell", "am", "start", "-n", "br.com.saveit/.MainActivity"]);
  const apk = await connect(9224);
  const html = await connect(9223);
  try {
    const data = await apk.evaluate(`JSON.parse(localStorage.getItem("save-it:desktop:v1"))`);
    assert.ok(data.bookmarks.some((link) => link.url === "https://example.org/migracao"), "O link da versão anterior foi migrado");
    console.log("Migração verificada no APK.");
    execFileSync("adb", ["shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "http://127.0.0.1:8765/Save-It.html", "-p", "com.android.chrome"]);
    // Give the two pages exactly the same collection before comparing any layout.
    await html.evaluate(`localStorage.setItem("save-it:desktop:v1", ${JSON.stringify(JSON.stringify(data))}); location.reload()`);
    let firstTab;
    for (let i = 0; i < 15 && !firstTab; i++) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      firstTab = await html.evaluate(`Boolean(document.querySelector("[data-nav=home]"))`);
    }
    assert.ok(firstTab, "O HTML reiniciou com os mesmos dados");
    for (const tab of ["home", "folders", "done", "settings"]) {
      const layouts = [];
      for (const [name, page] of [["APK", apk], ["Chrome", html]]) {
        const layout = await page.evaluate(`(async () => {
          document.querySelector('[data-nav="${tab}"]').click();
          await document.fonts.ready;
          return { width: innerWidth, elements: ["h1", ".topbar", ".toolbar", ".hero", ".dock"].map(selector => {
            const el = document.querySelector(selector);
            if (!el) return null;
            const { x, y, width, height } = el.getBoundingClientRect();
            const { color, backgroundColor, fontSize, fontWeight, boxShadow } = getComputedStyle(el);
            return { x, y: selector === ".dock" ? y - innerHeight : y, width, height, color, backgroundColor, fontSize, fontWeight, boxShadow };
          }) };
        })()`);
        layouts.push(layout);
        console.log(`${name} renderizou ${tab}`);
      }
      assert.deepEqual(layouts[0], layouts[1], `Layout em ${tab}`);
      console.log(`${tab}: mesma geometria e estilos no APK e no Chrome Android`);
    }
  } finally {
    clearTimeout(deadline);
    apk.close();
    html.close();
  }
})().catch((error) => { console.error(error); process.exit(1); });
