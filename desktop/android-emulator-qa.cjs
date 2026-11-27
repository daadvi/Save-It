const assert = require("node:assert/strict");
const { chromium } = require("/tmp/androidqa/node_modules/playwright-core");

(async () => {
  const webview = await chromium.connectOverCDP("http://127.0.0.1:9224", { timeout: 20000 });
  const chrome = await chromium.connectOverCDP("http://127.0.0.1:9223", { timeout: 20000 });
  const apk = webview.contexts()[0].pages().find((page) => page.url().includes("Save-It.html"));
  const html = chrome.contexts()[0].pages().find((page) => page.url().includes("Save-It.html"));
  assert.ok(apk && html, "Open the APK and the HTML in Android Chrome first");
  const data = await apk.evaluate(() => JSON.parse(localStorage.getItem("save-it:desktop:v1")));
  assert.ok(data.bookmarks.some((link) => link.url === "https://example.org/migracao"));
  await html.evaluate((state) => localStorage.setItem("save-it:desktop:v1", JSON.stringify(state)), data);
  await html.reload();
  async function layout(page) {
    return page.evaluate(() => {
      const rect = (selector) => {
        const el = document.querySelector(selector);
        if (!el) return null;
        const {x, y, width, height} = el.getBoundingClientRect();
        const { color, backgroundColor, fontSize, fontWeight, boxShadow } = getComputedStyle(el);
        return { x,y,width,height,color,backgroundColor,fontSize,fontWeight,boxShadow };
      };
      return { width: innerWidth, height: innerHeight, elements: ["h1",".topbar",".toolbar",".hero",".dock"].map(rect) };
    });
  }
  for (const tab of ["home", "folders", "done", "settings"]) {
    for (const page of [apk, html]) {
      await page.locator(`[data-nav="${tab}"]`).click({ timeout: 20000 });
      await page.screenshot({path: `/tmp/android-${page === apk ? "apk" : "chrome"}-${tab}.png`, timeout: 20000});
    }
    const a = await layout(apk);
    const h = await layout(html);
    assert.equal(a.width, h.width);
    for (let i=0; i<a.elements.length; i++) {
      const one = a.elements[i], two = h.elements[i];
      if (!one || !two) { assert.equal(one, two); continue; }
      if (i === 4) {
        // Each dock is anchored to its viewport, whose height differs due to Chrome's address bar.
        one.y -= a.height; two.y -= h.height;
      }
      assert.deepEqual(one, two, `Layout mismatch in ${tab} at element ${i}`);
    }
    console.log(`${tab}: same layout and styling`);
  }
  process.exit(0);
})().catch((error) => { console.error(error); process.exit(1); });
