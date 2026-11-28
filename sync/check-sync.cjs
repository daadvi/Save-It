const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { chromium, devices } = require("playwright-core");

const auth = "http://127.0.0.1:9099";
const firestore = "http://127.0.0.1:8084/v1/projects/demo-save-it/databases/(default)/documents";
const origin = "http://127.0.0.1:8120";
const password = "Senha-local-12345";
const stamp = Date.now();
const email = `coleta-${stamp}@example.com`;
const otherEmail = `outra-${stamp}@example.com`;
const html = readFileSync(process.argv[2] || "Save-It.html", "utf8");

async function authRequest(action, body) {
  const response = await fetch(auth + "/identitytoolkit.googleapis.com/v1/accounts:" + action + "?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  assert.equal(response.status, 200, action);
  return response.json();
}

async function verify(email) {
  const response = await fetch(auth + "/emulator/v1/projects/demo-save-it/oobCodes");
  const { oobCodes } = await response.json();
  const code = oobCodes.find(item => item.email === email && item.requestType === "VERIFY_EMAIL");
  assert.ok(code);
  await authRequest("update", { oobCode: code.oobCode });
}

async function open(browser, options = {}) {
  const page = await browser.newPage(options.mobile ? devices["Pixel 7"] : {});
  if (process.env.SAVE_IT_DEBUG) {
    page.on("pageerror", console.error);
    page.on("console", message => { if (message.type() === "error") console.error(message.text()); });
    page.on("requestfailed", request => console.error(request.url(), request.failure()));
    page.on("response", async response => {
      if (response.status() >= 400) console.error(response.status(), response.url(), await response.text());
    });
  }
  await page.emulateMedia({ reducedMotion: "reduce" });
  if (options.legacy) {
    await page.addInitScript(() => {
      const key = "save-it:desktop:v1";
      if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({
        folders: [{ id: "original", name: "Pasta antiga", parentId: null, color: "#c9b9ff" }],
        bookmarks: [{
          id: "original-link", url: "https://example.org/original", title: "Link guardado",
          createdAt: 1, completedAt: 2, folderId: "original", description: "Descrição guardada",
          note: "Nota guardada", type: "site", done: true,
        }],
        prefs: { remote: false },
      }));
    });
  }
  // Resolve emulator traffic from Node so managed Chrome's loopback permission does not affect tests.
  await page.route(/^http:\/\/127\.0\.0\.1:(9099|8084)\//, async route => {
    const request = route.request();
    const response = await fetch(request.url(), {
      method: request.method(),
      headers: request.headers(),
      body: request.postData() || undefined,
    });
    await route.fulfill({
      status: response.status,
      headers: { "Access-Control-Allow-Origin": "*", "Content-Type": "application/json" },
      body: await response.text(),
    });
  });
  await page.route(origin + "/", route => route.fulfill({ contentType: "text/html", body: html }));
  await page.goto(origin + "/");
  return page;
}

async function credentials(page, email, submit = "Entrar") {
  if (submit === "Criar conta") {
    await page.getByRole("button", { name: submit, exact: true }).click();
    await page.getByRole("heading", { name: "Crie sua conta", exact: true }).waitFor();
    assert.equal(await page.getByRole("button", { name: "Redefinir senha" }).isVisible(), false);
    assert.equal(await page.getByRole("button", { name: "Entrar", exact: true }).isVisible(), false);
  }
  const panel = page.locator("[data-account-panel]:not([hidden])");
  await panel.getByLabel("E-mail").fill(email);
  await panel.getByLabel("Senha", { exact: true }).fill(password);
  if (submit === "Criar conta") {
    await panel.getByLabel("Confirme a senha").fill("senha-diferente");
    await panel.getByRole("button", { name: submit, exact: true }).click();
    await page.getByText("As senhas precisam ser iguais.", { exact: true }).waitFor();
    await panel.getByLabel("Confirme a senha").fill(password);
  }
  await panel.getByRole("button", { name: submit, exact: true }).click();
}

async function tutorial(page) {
  await page.getByRole("heading", { name: "Como usar o Save It!" }).waitFor();
  const next = page.locator("[data-guide-next]");
  const back = page.locator("[data-guide-back]");
  assert.equal(await back.isDisabled(), true);
  await next.click();
  await page.getByRole("heading", { name: "Organize suas pastas" }).waitFor();
  await back.click();
  await page.getByRole("heading", { name: "Salve um link" }).waitFor();
  for (const title of ["Organize suas pastas", "Encontre o que guardou", "Marque como concluído", "Continue em outro dispositivo"]) {
    await next.click();
    await page.getByRole("heading", { name: title }).waitFor();
  }
  await page.getByRole("button", { name: "Entendi", exact: true }).click();
  await page.locator("#modal").waitFor({ state: "hidden" });
}

async function sync(page) {
  await page.locator('[data-nav="settings"]').click();
  await page.getByRole("button", { name: "Sincronizar agora" }).click();
  await page.waitForFunction(() => document.querySelector("#sync-status")?.textContent === "Sincronizado");
}

(async () => {
  const browser = await chromium.launch(process.env.SAVE_IT_CHROMIUM
    ? { executablePath: process.env.SAVE_IT_CHROMIUM } : { channel: "chrome" });
  try {
    const computer = await open(browser, { legacy: true });
    const phone = await open(browser, { mobile: true });
    await computer.getByRole("heading", { name: "Entre no Save It!" }).waitFor();
    assert.equal(await computer.locator(".dock").isVisible(), false);
    await credentials(computer, email, "Criar conta");
    await computer.getByRole("heading", { name: "Entre no Save It!" }).waitFor();
    assert.equal(await computer.locator('[data-account-panel="login"] input[name="email"]').inputValue(), email);
    assert.equal(await computer.locator('[data-account-panel="login"] input[name="password"]').inputValue(), "");
    await computer.getByText("Confirme seu e-mail pelo link enviado.", { exact: false }).waitFor({timeout:20000})
      .catch(async error => { throw new Error(await computer.locator("#account-message").textContent(), { cause: error }); });
    const unverified = await authRequest("signInWithPassword", { email, password, returnSecureToken: true });
    const denied = await fetch(firestore + "/users/" + unverified.localId + "/records", {
      headers: { Authorization: "Bearer " + unverified.idToken },
    });
    assert.equal(denied.status, 403);
    await verify(email);

    await credentials(computer, email);
    await tutorial(computer);
    await credentials(phone, email);
    await tutorial(phone);
    await phone.locator('[data-nav="done"]').click();
    await phone.locator(".card").getByText("Link guardado", { exact: true }).click();
    await phone.getByText("Nota guardada", { exact: true }).waitFor();
    await phone.getByRole("button", { name: "Fechar", exact: true }).last().click();
    await phone.locator('[data-nav="folders"]').click();
    await phone.getByRole("heading", { name: "Pasta antiga", exact: true }).waitFor();

    await phone.locator('[data-nav="home"]').click();
    await phone.getByRole("button", { name: "Salvar link", exact: true }).click();
    await phone.locator('#link-form input[name="url"]').fill("https://example.org/mobile");
    await phone.locator('#link-form input[name="title"]').fill("Enviado do Android");
    await phone.locator('#link-form button[type="submit"]').click();
    await sync(phone);
    await sync(computer);
    await computer.locator('[data-nav="home"]').click();
    await computer.getByText("Enviado do Android", { exact: true }).waitFor();
    await computer.locator(".card").filter({ hasText: "Enviado do Android" }).locator("[data-complete]").click();
    await sync(computer);
    await sync(phone);
    await phone.locator('[data-nav="done"]').click();
    await phone.getByText("Enviado do Android", { exact: true }).waitFor();

    const foreign = await authRequest("signUp", { email: otherEmail, password, returnSecureToken: true });
    await authRequest("sendOobCode", { requestType: "VERIFY_EMAIL", idToken: foreign.idToken });
    await verify(otherEmail);
    const foreignVerified = await authRequest("signInWithPassword", { email: otherEmail, password, returnSecureToken: true });
    const crossAccount = await fetch(firestore + "/users/" + unverified.localId + "/records", {
      headers: { Authorization: "Bearer " + foreignVerified.idToken },
    });
    assert.equal(crossAccount.status, 403);
    await computer.locator('[data-nav="settings"]').click();
    await computer.getByRole("button", { name: "Sair da conta" }).click();
    await credentials(computer, otherEmail);
    await tutorial(computer);
    await computer.locator('[data-nav="done"]').click();
    assert.equal(await computer.locator(".card").count(), 0);

    await phone.reload();
    await phone.locator('[data-nav="done"]').click();
    assert.equal(await phone.locator(".card").count(), 2);
    assert.equal(await phone.getByRole("heading", { name: "Como usar o Save It!" }).isVisible(), false);
    console.log("Cadastro, confirmação, tutorial, migração, sincronização e separação de contas verificados.");
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
