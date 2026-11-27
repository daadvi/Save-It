// Exercise Android's document picker with a generated Savr backup.
const assert = require("node:assert/strict");
const { writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { setTimeout: delay } = require("node:timers/promises");
const { adb, inspectDevice } = require("./android-test-device.cjs");

const name = "savr-test.json";
const addresses = Array.from({length: 47}, (_, index) => "https://example.org/document/" + index);
const fixture = {
  bookmarks: addresses.map(url => ({ url, title: "Página", imageUrl: url + ".jpg" })),
  collections: [{name: "Leituras", bookmarkUrls: addresses}],
};

async function pickDocument() {
  let openedDrawer = false;
  let lastTree = "";
  for (let attempt = 0; attempt < 15; attempt++) {
    await delay(500);
    const dump = adb("shell", "uiautomator", "dump", "/sdcard/picker.xml");
    if (!dump.includes("dumped to")) continue;
    lastTree = adb("shell", "cat", "/sdcard/picker.xml");
    const nodes = lastTree.match(/<node\s[^>]+>/g) || [];
    const file = nodes.find(node => node.includes('text="' + name + '"'));
    const drawer = nodes.find(node => /content-desc="Show roots"|content-desc="Open navigation drawer"/i.test(node));
    const downloads = nodes.find(node => node.includes('text="Downloads"'));
    const target = file || (openedDrawer ? downloads : drawer);
    if (!target) continue;
    const [, x1, y1, x2, y2] = target.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).map(Number);
    adb("shell", "input", "tap", String((x1+x2)>>1), String((y1+y2)>>1));
    if (file) return;
    openedDrawer = true;
  }
  throw Error("Arquivo de teste ausente no seletor: " + lastTree);
}

(async () => {
  const local = join(tmpdir(), name);
  writeFileSync(local, JSON.stringify(fixture, null, 2));
  adb("push", local, "/sdcard/Download/" + name);
  adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Download/" + name);
  adb("shell", "input", "keyevent", "KEYCODE_WAKEUP");
  adb("shell", "wm", "dismiss-keyguard");
  adb("shell", "am", "start", "-W", "-n", "br.com.saveit/.MainActivity");
  await inspectDevice(async (evaluate, waitFor) => {
    await waitFor('!!document.querySelector("[data-nav=settings]")');
    await evaluate('document.querySelector("[data-nav=settings]").click()');
    await evaluate('document.querySelector("#theme + button").click()');
    await waitFor('document.querySelector("#choice-dialog").open');
    const motion = await evaluate(`(() => {
      const dialog = document.querySelector("#choice-dialog");
      const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
      const animation = getComputedStyle(dialog).animationName;
      const slide = dialog.getAnimations().find(animation => animation.animationName === "modal-enter");
      if (!slide) return { reduced, animation, rise: 0 };
      slide.pause();
      slide.currentTime = 0;
      const rise = new DOMMatrix(getComputedStyle(dialog).transform).m42;
      slide.play();
      return { reduced, animation, rise };
    })()`);
    if (motion.reduced) assert.equal(motion.animation, "none");
    else assert.ok(motion.rise >= 48, "O seletor começa abaixo da posição final: " + JSON.stringify(motion));
    console.log("Animação do seletor:", JSON.stringify(motion));
    assert.equal(await evaluate('document.querySelector("#choice-options [aria-pressed=true]").textContent.trim()'), "Claro");
    await evaluate('document.querySelectorAll("#choice-options button")[1].click()');
    await waitFor('document.documentElement.classList.contains("dark")');
    assert.equal(await evaluate('document.querySelector("#choice-dialog").open'), false);
    await evaluate('if (document.querySelector("#remote").checked) document.querySelector("#remote").click()');
    await evaluate('document.querySelector("[data-import=links]").click()');
    await pickDocument();
    await waitFor('document.querySelector("#modal")?.open');
    assert.deepEqual(await evaluate('[...document.querySelectorAll(".import-summary b")].map(x=>x.textContent)'), ["47", "0"]);
    await evaluate('document.querySelector("#confirm-import").click()');
    await waitFor('!!document.querySelector("[data-nav=home][aria-current=page]")');
    const result = await evaluate('JSON.parse(localStorage.getItem("save-it:desktop:v1")).bookmarks.filter(b => b.url.includes("/document/"))');
    assert.deepEqual(result.map(b => b.url), addresses);
    assert.ok(result.every(b => b.folderId === null && !b.done));
    console.log("Seletor do Android importou 47 páginas do JSON, sem duplicatas e sem miniaturas extras.");
  });
})().catch(err => { console.error(err); process.exitCode = 1; });
