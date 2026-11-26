// Run with playwright-core and an installed Google Chrome.
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const { pathToFileURL, fileURLToPath } = require('node:url');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');

function unpack(zip) {
  const entries = new Map();
  for (let p = 0; zip.readUInt32LE(p) === 0x04034b50;) {
    const length = zip.readUInt32LE(p+18);
    const nameLength = zip.readUInt16LE(p+26);
    const name = zip.toString('utf8',p+30,p+30+nameLength);
    const start = p+30+nameLength+zip.readUInt16LE(p+28);
    const data = zip.subarray(start,start+length);
    assert.equal(zlib.crc32(data),zip.readUInt32LE(p+14));
    entries.set(name,data);
    p=start+length;
  }
  return entries;
}

(async () => {
  const tmp = await fs.mkdtemp(path.join(os.tmpdir(),'Save It '));
  const file = path.join(tmp,'Café & links','Save-It.html');
  await fs.mkdir(path.dirname(file));
  await fs.copyFile(path.join(__dirname,'Save-It.html'),file);
  const ctx = await chromium.launchPersistentContext(path.join(tmp,'profile'), {
    headless: true, channel: 'chrome', offline: true, acceptDownloads: true
  });
  try {
    const page = await ctx.newPage();
    const errors = [], startupRequests = [];
    page.on('pageerror', err => errors.push(err.message));
    page.on('request', request => startupRequests.push(request.url()));
    await page.goto(pathToFileURL(file).href);
    await page.locator('#create-shortcut').click();
    const download = page.waitForEvent('download');
    await page.locator('#shortcut-zip').click();
    const entries = unpack(await fs.readFile(await (await download).path()));
    assert.equal(entries.get('Save-It.ico').readUInt16LE(2),1);
    const shortcut = entries.get('Save It!.url').toString('utf16le');
    const address = shortcut.match(/^URL=(.+)\r$/m)[1];
    assert.equal(fileURLToPath(address),file);
    if (process.platform === 'win32') assert.ok(shortcut.includes('IconFile='+path.join(path.dirname(file),'Save-It.ico')+'\r\n'));
    assert.ok(startupRequests.every(url => !/^https?:/.test(url)));
    await page.keyboard.press('Escape');
    await page.locator('[data-nav=settings]').click();
    await page.locator('#remote').uncheck();
    const data = { folders:[{id:'f',name:'Leituras',parentId:null}], bookmarks:[{url:'https://example.org/teste',title:'Do Android',folderId:'f',done:true,note:'Uma nota'}] };
    await page.locator('#import-file').setInputFiles({name:'backup.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(data))});
    await page.locator('#confirm-import').click();
    await page.locator('[data-nav=done]').click();
    await page.locator('[data-complete]').click();
    await page.reload();
    await page.locator('article').waitFor();
    assert.ok((await page.locator('article').innerText()).includes('Do Android'));
    await page.locator('[data-nav=settings]').click();
    const exported = page.waitForEvent('download');
    await page.locator('[data-export=json]').click();
    const backup = JSON.parse(await fs.readFile(await (await exported).path(),'utf8'));
    assert.equal(backup.bookmarks[0].done,false);
    assert.equal(backup.bookmarks[0].note,'Uma nota');
    assert.equal(backup.bookmarks[0].folderId,backup.folders[0].id);
    assert.deepEqual(errors,[]);
  } finally {
    await ctx.close();
    await fs.rm(tmp,{recursive:true,force:true});
  }
})().catch(err => { console.error(err);process.exitCode=1; });
