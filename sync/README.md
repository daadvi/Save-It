# Conta e sincronização do Save It!

O HTML para computador e o APK usam a mesma configuração pública de um aplicativo Web do Firebase. O Firebase Authentication gerencia as senhas, a confirmação de e-mail e a recuperação da conta. O Cloud Firestore guarda marcadores, pastas e preferências.

## Configuração de produção

1. Abra o projeto do Save It! no Firebase e registre um aplicativo **Web**. Copie `projectId` e `apiKey` do objeto `firebaseConfig` seguindo [firebase-config.example.json](firebase-config.example.json). São valores públicos do cliente.
2. Em **Authentication → Sign-in method**, ative **E-mail/senha**. Em **Authentication → Templates**, revise os e-mails de confirmação e redefinição. O link de confirmação usa a página hospedada pelo Firebase.
3. Crie o banco **Cloud Firestore (default)**. Aplique o arquivo [firestore.rules](firestore.rules) na aba **Regras**. Pelo terminal autenticado, execute `npx firebase-tools deploy --only firestore:rules --project <projectId> --config sync/firebase.json` na raiz. Use um projeto dedicado, pois a publicação substitui as regras do banco padrão.
4. Gere o HTML e use esse mesmo arquivo para montar o APK.

```sh
node sync/build-html.mjs firebase-config.json /caminho/Save-It.html
./gradlew -PsaveItWebHtml=/caminho/Save-It.html testDebugUnitTest assembleDebug
```

O login é obrigatório no HTML gerado com a configuração do Firebase. O arquivo-fonte em `desktop/Save-It.html` pode ser aberto em modo local para desenvolvimento. A primeira abertura em cada dispositivo precisa de conexão. Uma sessão já validada pode abrir sua cópia local sem internet e enviar as alterações na reconexão.

Os dados locais de uma versão anterior são incorporados à primeira conta validada no dispositivo e permanecem disponíveis na cópia antiga. O tutorial aparece depois da primeira entrada na conta em cada dispositivo e também pode ser reaberto em Configurações.

## Atualizações entre dispositivos

Cada pasta, marcador e preferência é sincronizado de forma independente. Alterações locais pendentes sobre um mesmo registro prevalecem quando esse dispositivo volta a sincronizar. Exclusões são propagadas com registros de exclusão. As regras permitem leituras e gravações apenas ao dono da coleção com e-mail confirmado.

## Validação com emulador

```sh
npx firebase-tools emulators:start --only auth,firestore --project demo-save-it --config sync/firebase.json
```

Em outro terminal, use um JSON de teste com `"projectId": "demo-save-it"`, `"apiKey": "demo-key"` e `"emulator": true`. Gere o HTML e rode `node sync/check-sync.cjs /caminho/Save-It.html` com `playwright-core` e Chrome instalados. O teste confirma o e-mail pelo emulador, abre duas sessões independentes e verifica que uma segunda conta não acessa a coleção da primeira.
