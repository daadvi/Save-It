const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const html = fs.readFileSync(__dirname + "/Save-It.html", "utf8");
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const context = vm.createContext({ URL, crypto, console, setTimeout, clearTimeout });
const start = script.indexOf("const uid");
assert.ok(start >= 0);
const end = script.indexOf("function parseHTML");
assert.ok(end > start);
const core = script.slice(start, end);
const detection = script.slice(script.indexOf("function detectType"), script.indexOf("function initialImage"));
vm.runInContext(core + detection + "\nglobalThis.parse = parseJSONBackup; globalThis.merge = mergeBackup;", context);
const parse = (value) => JSON.parse(JSON.stringify(context.parse(typeof value === "string" ? value : JSON.stringify(value))));
const sample = {
  bookmarks: [
    { url: "https://example.org/a", title: "Artigo", description: "Minha nota", imageUrl: "https://example.org/a.jpg", createdAt: 1700000000000 },
    { url: "https://example.org/b", title: "Outro", createdAt: 1700000001000 },
  ],
  collections: [{ name: "Leituras", bookmarkUrls: ["https://example.org/a"] }],
};
test("Savr preserva títulos, notas, miniaturas, datas e pertencimento às coleções", () => {
  const data = parse(sample);
  assert.equal(data.format, "Savr");
  assert.equal(data.invalid, 0);
  assert.equal(data.bookmarks[0].folderId, data.folders[0].id);
  assert.equal(data.bookmarks[0].note, "Minha nota");
  assert.equal(data.bookmarks[0].image, sample.bookmarks[0].imageUrl);
  assert.equal(data.bookmarks[0].createdAt, sample.bookmarks[0].createdAt);
  assert.equal(data.bookmarks[1].folderId, null);
});
test("BOM, referências sem barra final, coleções vazias e duplicados são tratados", () => {
  const data = parse("\uFEFF" + JSON.stringify({
    bookmarks: [{ url: "https://example.org", title: "Teste" }, { url: "https://example.org/" }],
    collections: [{ name: "Pasta", bookmarkUrls: ["https://example.org/"] }, { name: "Vazia", bookmarkUrls: [] }],
  }));
  assert.equal(data.folders.length, 2);
  assert.equal(data.bookmarks[0].folderId, data.folders[0].id);
  assert.equal(context.merge({ bookmarks: [], folders: [] }, data).bookmarks.length, 1);
});
test("links inválidos são contados, e vínculos ambíguos são informados na prévia", () => {
  const data = parse({
    bookmarks: [...sample.bookmarks, { url: "javascript:alert(1)" }],
    collections: [
      ...sample.collections,
      { name: "Segunda", bookmarkUrls: ["https://example.org/a", "https://example.org/ausente"] },
    ],
  });
  assert.equal(data.invalid, 1);
  assert.equal(data.warnings.length, 2);
  assert.equal(data.bookmarks[0].folderId, data.folders[0].id);
});
test("backup Save It! preserva conclusão e distingue JSON malformado de formato desconhecido", () => {
  const native = parse({ folders: [], bookmarks: [{ url: "https://example.org", done: true, completedAt: 1234 }] });
  assert.equal(native.bookmarks[0].done, true);
  assert.equal(native.bookmarks[0].completedAt, 1234);
  assert.throws(() => parse("{bad"), /sintaxe/);
  assert.throws(() => parse({ other: [] }), /formato não foi reconhecido/);
});
test("títulos e descrições longos são preservados integralmente", () => {
  const bookmark = { ...sample.bookmarks[0], title: "Título ".repeat(100), description: "Descrição ".repeat(600) };
  const data = parse({ bookmarks: [bookmark], collections: sample.collections });
  assert.equal(data.bookmarks[0].title, bookmark.title);
  assert.equal(data.bookmarks[0].note, bookmark.description);
});
