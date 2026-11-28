import { readFile, writeFile } from "node:fs/promises";

const [configPath, output] = process.argv.slice(2);
if (!configPath || !output) throw new Error("Uso: node sync/build-html.mjs firebase-config.json Save-It.html");
const config = JSON.parse(await readFile(configPath, "utf8"));
const { projectId, apiKey } = config;
if (!/^[a-z0-9-]+$/.test(projectId || "") || !/^[\w-]+$/.test(apiKey || "")) {
  throw new Error("A configuração precisa do projectId e da apiKey do aplicativo Web do Firebase.");
}
const emulator = config.emulator === true;
if (emulator && !projectId.startsWith("demo-")) throw new Error("Use um ID demo-* no emulador.");
const source = await readFile(new URL("../desktop/Save-It.html", import.meta.url), "utf8");
const account = await readFile(new URL("./account.js", import.meta.url), "utf8");
const settings = JSON.stringify({ projectId, apiKey, emulator });
const placeholder = "<!-- SAVE_IT_ACCOUNT -->";
if (!source.includes(placeholder)) throw new Error("Ponto de inclusão da conta ausente.");
const html = source.replace(placeholder, () => `<script>window.SaveItFirebase = ${settings};\n${account}\n</script>`);
await writeFile(output, html.replace(/\r\n/g, "\n"));
