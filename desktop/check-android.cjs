const assert = require("node:assert/strict");
const { chromium, devices } = require("playwright-core");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");

const html = readFileSync(join(__dirname, "Save-It.html"), "utf8");
const url = "https://appassets.androidplatform.net/Save-It.html";

async function open(browser, native = false) {
  const page = await browser.newPage({ ...devices["Pixel 7"] });
  await page.route(url, (route) => route.fulfill({ contentType: "text/html", body: html }));
  if (native) {
    await page.addInitScript(() => {
      window.messages = [];
      window.SaveItAndroid = {
        postMessage(raw) {
          const message = JSON.parse(raw);
          window.messages.push(message);
          if (message.action === "ready") {
            this.onmessage({ data: JSON.stringify(window.androidBootstrap) });
          }
        },
      };
      window.androidBootstrap = {};
    });
  }
  await page.goto(url);
  return page;
}

async function test() {
  const browser = await chromium.launch(process.env.SAVE_IT_CHROMIUM
    ? { executablePath: process.env.SAVE_IT_CHROMIUM }
    : { channel: "chrome" });
  try {
    const htmlPage = await open(browser);
    const apkPage = await open(browser, true);
    for (const tab of ["home", "folders", "done", "settings"]) {
      const selector = `[data-nav="${tab}"]`;
      for (const page of [htmlPage, apkPage]) {
        await page.locator(selector).click();
        await page.evaluate(() => document.fonts.ready);
      }
      const layout = (page) => page.evaluate(() =>
        [...document.querySelectorAll(".topbar, main, main *, .dock, .dock *")].map((node) => {
          const rect = node.getBoundingClientRect();
          const css = getComputedStyle(node);
          return [node.tagName, rect.x, rect.y, rect.width, rect.height,
            css.color, css.backgroundColor, css.fontSize, css.boxShadow];
        }));
      assert.deepEqual(await layout(apkPage), await layout(htmlPage), `Mobile layouts differ in ${tab}`);
    }

    const requested = [];
    await htmlPage.route("https://api.microlink.io/**", async (route) => {
      requested.push(new URL(route.request().url()).searchParams.get("url"));
      await route.fulfill({ json: { status: "success", data: {
        title: "Do site", description: "Descrição publicada", image: { url: "https://example.org/nova.jpg" },
      } } });
    });
    async function importBookmarks(name, text) {
      await htmlPage.locator("#import-file").setInputFiles({
        name, mimeType: name.endsWith("json") ? "application/json" : "text/html", buffer: Buffer.from(text),
      });
      await htmlPage.locator("#confirm-import").click();
    }
    const photo = "https://example.org/foto.jpg";
    await importBookmarks("links.json", JSON.stringify({ folders: [], bookmarks: [
      { url: "https://example.org/sem-descricao", title: "Meu título", image: photo, note: "Nota pessoal" },
      { url: "https://example.org/completa", image: photo, description: "Do backup" },
    ] }));
    await importBookmarks("links.html", '<DL><DT><A HREF="https://example.org/html">Página</A></DL>');
    await htmlPage.locator('[data-nav="home"]').click();
    await htmlPage.waitForFunction(() =>
      [...document.querySelectorAll(".card-description")].filter(el => el.textContent === "Descrição publicada").length === 2);
    await htmlPage.reload();
    const imported = await htmlPage.evaluate(() => JSON.parse(localStorage.getItem("save-it:desktop:v1")).bookmarks);
    assert.deepEqual(requested.sort(), ["https://example.org/html", "https://example.org/sem-descricao"]);
    assert.equal(imported[0].note, "Nota pessoal");
    assert.equal(imported[0].image, photo);
    assert.equal(imported[0].title, "Meu título");
    assert.equal(imported[0].description, "Descrição publicada");
    assert.equal(imported[1].description, "Do backup");

    await apkPage.evaluate(() => {
      window.androidBootstrap = {
        legacy: {
          folders: [{ id: "folder", name: "Favoritos", parentId: null }],
          bookmarks: [{
            id: "saved", url: "https://example.org", title: "Meu link", folderId: "folder",
            note: "Uma nota", done: true, completedAt: 42, createdAt: 1, image: "",
          }],
        },
        compact: true,
        accent: "#ffb6cb",
        shared: ["https://example.net", "https://example.org/"],
      };
      window.dispatchEvent(new Event("save-it-android-resume"));
    });
    await apkPage.getByText("example.net", { exact: true }).first().waitFor();
    const state = await apkPage.evaluate(() => JSON.parse(localStorage.getItem("save-it:desktop:v1")));
    assert.equal(state.bookmarks.length, 2);
    assert.equal(state.bookmarks[0].note, "Uma nota");
    assert.equal(state.bookmarks[0].folderId, "folder");
    assert.equal(state.bookmarks[0].completedAt, 42);
    assert.equal(state.prefs.accent, "#ffb6cb");
    assert.equal(state.prefs.density, "compact");

    await apkPage.locator('[data-nav="settings"]').click();
    await apkPage.locator('[data-export="json"]').click();
    const exported = await apkPage.evaluate(() => window.messages.find((message) => message.action === "export"));
    assert.ok(exported.name.endsWith(".json"));
    assert.equal(JSON.parse(exported.content).bookmarks.length, 2);

    await apkPage.reload();
    assert.equal(await apkPage.evaluate(() => document.documentElement.style.getPropertyValue("--accent")), "#ffb6cb");
  } finally {
    await browser.close();
  }
}

test().catch((error) => { console.error(error); process.exitCode = 1; });
