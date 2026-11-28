(() => {
  "use strict";

  const { apiKey, projectId, emulator } = window.SaveItFirebase;
  const authHost = emulator ? "http://127.0.0.1:9099/identitytoolkit.googleapis.com" : "https://identitytoolkit.googleapis.com";
  const sessionHost = emulator ? "http://127.0.0.1:9099/securetoken.googleapis.com" : "https://securetoken.googleapis.com";
  const database = "projects/" + projectId + "/databases/(default)";
  const firestore = (emulator ? "http://127.0.0.1:8084" : "https://firestore.googleapis.com") + "/v1/" + database;
  const storagePrefix = "save-it:account:" + projectId + ":";
  const sessionKey = storagePrefix + "session";
  const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
  const read = key => JSON.parse(localStorage.getItem(key) || "null");
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

  async function request(url, params, token) {
    const headers = {};
    let body;
    if (params instanceof URLSearchParams) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      body = params;
    } else if (params !== undefined) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(params);
    }
    if (token) headers.Authorization = "Bearer " + token;
    const response = await fetch(url, { method: body ? "POST" : "GET", headers, body, signal: AbortSignal.timeout(15000), cache: "no-store" });
    const result = await response.json();
    if (!response.ok) throw Object.assign(new Error(result.error?.message || "Erro na conexão."), { status: response.status });
    return result;
  }
  function auth(action, body) {
    return request(authHost + "/v1/accounts:" + action + "?key=" + apiKey, body);
  }
  function fromAuth(result) {
    return {
      uid: result.localId, email: result.email,
      token: result.idToken, refresh: result.refreshToken,
      expires: Date.now() + Number(result.expiresIn) * 1000,
    };
  }
  function records(state) {
    const indexed = Object.create(null);
    for (const folder of state.folders) indexed["f:" + folder.id] = { ...folder };
    for (const bookmark of state.bookmarks) indexed["b:" + bookmark.id] = { ...bookmark };
    for (const key of ["accent", "theme", "density", "remote"]) {
      if (own(state.prefs, key)) indexed["p:" + key] = state.prefs[key];
    }
    return indexed;
  }
  function collection(indexed, migrated) {
    const state = { version: 1, folders: [], bookmarks: [], prefs: {}, androidMigrationComplete: migrated };
    for (const [key, data] of Object.entries(indexed)) {
      if (key.startsWith("f:") && data?.id === key.slice(2)) state.folders.push({ ...data });
      if (key.startsWith("b:") && data?.id === key.slice(2)) state.bookmarks.push({ ...data });
      if (["p:accent", "p:theme", "p:density", "p:remote"].includes(key)) state.prefs[key.slice(2)] = data;
    }
    return state;
  }
  const keyEncoder = new TextEncoder();
  function documentId(key) {
    return btoa(String.fromCharCode(...keyEncoder.encode(key))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  window.startSaveItAccount = function(app) {
    const gate = document.querySelector("#account-screen");
    const loginPanel = gate.querySelector('[data-account-panel="login"]');
    const signupPanel = gate.querySelector('[data-account-panel="signup"]');
    const login = loginPanel.querySelector("form");
    const signup = signupPanel.querySelector("form");
    const message = gate.querySelector("#account-message");
    const buttons = [...gate.querySelectorAll("button")];
    const resend = login.querySelector('[value="resend"]');
    const cachedKey = uid => storagePrefix + "data:" + uid;
    let verification = null;
    let account = null;
    let timer;
    let busy = false;

    function clearPasswords() {
      for (const input of gate.querySelectorAll('input[type="password"]')) input.value = "";
    }

    function updateResend() {
      resend.hidden = verification?.email !== login.elements.email.value.trim();
    }

    function showPanel(mode, text = "", focus = true) {
      const showSignup = mode === "signup";
      const from = showSignup ? login : signup;
      const to = showSignup ? signup : login;
      if (!from.closest("section").hidden) to.elements.email.value = from.elements.email.value;
      loginPanel.hidden = showSignup;
      signupPanel.hidden = !showSignup;
      clearPasswords();
      updateResend();
      message.textContent = text;
      if (focus) to.closest("section").querySelector("h1").focus();
    }

    login.elements.email.addEventListener("input", updateResend);
    for (const button of gate.querySelectorAll("[data-account-mode]")) {
      button.addEventListener("click", () => showPanel(button.dataset.accountMode));
    }

    function showGate(text = "") {
      document.documentElement.classList.add("account-locked");
      gate.hidden = false;
      message.textContent = text;
    }

    function status(text) {
      if (account) account.status = text;
      const label = document.querySelector("#sync-status");
      if (label) label.textContent = text;
    }

    function store(account, cache) {
      localStorage.setItem(cachedKey(account.session.uid), JSON.stringify(cache));
      account.cache = cache;
    }

    async function freshToken(active) {
      const session = active.session;
      if (session.expires > Date.now() + 60000) return session.token;
      const result = await request(sessionHost + "/v1/token?key=" + apiKey,
        new URLSearchParams({ grant_type: "refresh_token", refresh_token: session.refresh }));
      if (account !== active) throw new Error("Sessão encerrada.");
      active.session = {
        ...session, token: result.id_token, refresh: result.refresh_token,
        expires: Date.now() + Number(result.expires_in) * 1000,
      };
      localStorage.setItem(sessionKey, JSON.stringify(active.session));
      return active.session.token;
    }

    function syncSoon(delay = 400) {
      clearTimeout(timer);
      if (account) timer = setTimeout(() => synchronize(account), delay);
    }

    async function pull(active, token) {
      const cursor = active.cache.cursor;
      const query = {
        from: [{ collectionId: "records" }],
        orderBy: [{ field: { fieldPath: "updatedAt" }, direction: "ASCENDING" }],
      };
      if (cursor) query.where = { fieldFilter: {
        field: { fieldPath: "updatedAt" }, op: "GREATER_THAN_OR_EQUAL",
        value: { timestampValue: cursor },
      } };
      const rows = await request(firestore + "/documents/users/" + encodeURIComponent(active.session.uid) + ":runQuery",
        { structuredQuery: query }, token);
      if (active !== account) return false;

      const data = { ...active.cache.data };
      let next = cursor;
      let changed = false;
      for (const { document } of rows) {
        if (!document) continue;
        const fields = document.fields;
        const key = fields.key.stringValue;
        next = fields.updatedAt.timestampValue;
        if (own(active.cache.pending, key)) continue;
        if (fields.data.nullValue === null || own(fields.data, "nullValue")) {
          if (own(data, key)) { delete data[key]; changed = true; }
        } else {
          const value = JSON.parse(fields.data.stringValue);
          if (!same(data[key], value)) { data[key] = value; changed = true; }
        }
      }
      if (changed || next !== cursor) store(active, { ...active.cache, data, cursor: next });
      return changed;
    }

    async function push(active, token) {
      const { data, pending: queued } = active.cache;
      const pending = Object.entries(queued).map(([key, id]) => ({ key, id, value: data[key] ?? null }));
      const path = database + "/documents/users/" + active.session.uid + "/records/";
      for (let index = 0; index < pending.length && active === account;) {
        const batch = [];
        let size = 0;
        while (index < pending.length && batch.length < 100 && size < 6 * 1024 * 1024) {
          const entry = pending[index++];
          size += keyEncoder.encode(JSON.stringify(entry)).byteLength;
          batch.push(entry);
        }
        const writes = batch.map(({ key, value }) => ({
          update: { name: path + documentId(key), fields: {
            key: { stringValue: key },
            data: value === null ? { nullValue: null } : { stringValue: JSON.stringify(value) },
          } },
          updateTransforms: [{ fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" }],
        }));
        await request(firestore + "/documents:commit", { writes }, token);
        if (active !== account) return;
        const remaining = { ...active.cache.pending };
        for (const { key, id } of batch) if (remaining[key] === id) delete remaining[key];
        store(active, { ...active.cache, pending: remaining });
      }
    }

    async function synchronize(active, first = false) {
      if (!active || active !== account || active.running) return;
      active.running = true;
      let nextDelay = 15000;
      try {
        status("Sincronizando…");
        const token = await freshToken(active);
        if (active !== account) return;
        let changed = await pull(active, token);
        if (active !== account) return;
        if (!localStorage.getItem(storagePrefix + "legacy-owner") && active.legacy) {
          const local = collection(active.cache.data, active.cache.migrated);
          save(app.migrate(local, active.legacy));
          localStorage.setItem(storagePrefix + "legacy-owner", active.session.uid);
          changed = true;
        }
        if (!first && changed) app.update(collection(active.cache.data, active.cache.migrated));
        await push(active, token);
        if (active !== account) return;
        status(Object.keys(active.cache.pending).length ? "Alterações pendentes" : "Sincronizado");
        if (first) activate(active);
        if (Object.keys(active.cache.pending).length) nextDelay = 400;
      } catch (error) {
        if (active !== account) return;
        if (error.status === 401 || /TOKEN_EXPIRED|INVALID_REFRESH_TOKEN|USER_DISABLED|USER_NOT_FOUND/.test(error.message)) {
          logout("Entre novamente para continuar.");
        } else if (first && !active.hadCache) {
          account = null;
          showGate("Não foi possível carregar a coleção. Verifique a conexão e entre novamente.");
        } else {
          status("Sincronização pendente. O app vai tentar novamente.");
          nextDelay = 30000;
          if (first) activate(active);
        }
      } finally {
        active.running = false;
        if (active === account) syncSoon(nextDelay);
      }
    }

    function activate(active) {
      gate.hidden = true;
      document.documentElement.classList.remove("account-locked");
      clearPasswords();
      app.open(collection(active.cache.data, active.cache.migrated));
    }

    function save(state) {
      if (!account) throw new Error("Entre na sua conta para salvar.");
      const snapshot = records(state);
      const previous = account.cache;
      const pending = { ...previous.pending };
      for (const key of new Set([...Object.keys(previous.data), ...Object.keys(snapshot)])) {
        if (!same(previous.data[key], snapshot[key])) pending[key] = crypto.randomUUID();
      }
      store(account, { ...previous, data: snapshot, pending, migrated: state.androidMigrationComplete === true });
      if (Object.keys(pending).length) {
        status("Alterações pendentes");
        if (!account.running) syncSoon();
      }
    }

    async function connect(session, legacy) {
      const saved = read(cachedKey(session.uid));
      account = {
        session, legacy, hadCache: Boolean(saved),
        cache: saved || { data: {}, pending: {}, cursor: null, migrated: false },
      };
      localStorage.setItem(sessionKey, JSON.stringify(session));
      showGate("Carregando sua coleção…");
      await synchronize(account, true);
    }

    function logout(text = "") {
      clearTimeout(timer);
      localStorage.removeItem(sessionKey);
      account = null;
      verification = null;
      showPanel("login", "", false);
      app.lock();
      showGate(text);
    }

    const errors = {
      INVALID_LOGIN_CREDENTIALS: "Confira o e-mail e a senha.",
      INVALID_PASSWORD: "Confira o e-mail e a senha.",
      EMAIL_NOT_FOUND: "Confira o e-mail e a senha.",
      EMAIL_EXISTS: "Já existe uma conta com esse e-mail. Entre ou redefina a senha.",
      TOO_MANY_ATTEMPTS_TRY_LATER: "Muitas tentativas. Tente novamente daqui a pouco.",
      OPERATION_NOT_ALLOWED: "O administrador precisa ativar a autenticação por e-mail.",
    };

    gate.addEventListener("submit", async event => {
      event.preventDefault();
      if (busy) return;
      const form = event.target;
      const { email, password, confirmation } = form.elements;
      const action = form === signup ? "signup" : event.submitter?.value || "login";
      if (action !== "resend" && !email.reportValidity()) return;
      if (["login", "signup"].includes(action) && !password.reportValidity()) return;
      if (action === "signup" && password.value.length < 8) {
        message.textContent = "Use pelo menos 8 caracteres na senha.";
        password.focus();
        return;
      }
      if (action === "signup" && !confirmation.reportValidity()) return;
      if (action === "signup" && confirmation.value !== password.value) {
        message.textContent = "As senhas precisam ser iguais.";
        confirmation.focus();
        return;
      }
      busy = true;
      buttons.forEach(button => button.disabled = true);
      message.textContent = "Aguarde…";
      try {
        if (action === "reset") {
          await auth("sendOobCode", { requestType: "PASSWORD_RESET", email: email.value.trim() })
            .catch(error => { if (!error.message.includes("EMAIL_NOT_FOUND")) throw error; });
          message.textContent = "Se a conta existir, você receberá um e-mail para redefinir a senha.";
        } else if (action === "resend") {
          if (!verification || verification.email !== email.value.trim()) return;
          await auth("sendOobCode", { requestType: "VERIFY_EMAIL", idToken: verification.token });
          message.textContent = "Confira sua caixa de entrada para confirmar o e-mail e entrar.";
        } else {
          const result = await auth(action === "signup" ? "signUp" : "signInWithPassword",
            { email: email.value.trim(), password: password.value, returnSecureToken: true });
          const session = fromAuth(result);
          const profile = await auth("lookup", { idToken: session.token });
          if (!profile.users?.[0]?.emailVerified) {
            verification = { email: email.value.trim(), token: session.token };
            const prompt = "Confirme seu e-mail pelo link enviado. Depois, volte aqui e escolha Entrar.";
            if (action === "signup") {
              showPanel("login");
              await auth("sendOobCode", { requestType: "VERIFY_EMAIL", idToken: session.token });
            }
            updateResend();
            message.textContent = prompt;
          } else {
            await connect(session, app.legacy);
          }
        }
      } catch (error) {
        message.textContent = errors[error.message] || "Não foi possível concluir. Verifique a conexão e tente novamente.";
      } finally {
        busy = false;
        buttons.forEach(button => button.disabled = false);
      }
    });

    window.addEventListener("online", () => syncSoon(0));
    window.addEventListener("save-it-android-resume", () => syncSoon(0));
    window.addEventListener("focus", () => syncSoon(0));
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) syncSoon(0);
    });

    return {
      save, logout,
      sync() { synchronize(account); },
      profile() { return account && { uid: account.session.uid, email: account.session.email, status: account.status }; },
      async start() {
        showGate();
        try {
          const session = read(sessionKey);
          if (session) await connect(session, app.legacy);
        } catch {
          logout("Não foi possível restaurar a sessão. Entre novamente.");
        }
      },
    };
  };
})();
