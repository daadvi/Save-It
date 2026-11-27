# Save It!

Organizador de marcadores que usa o mesmo HTML no computador e no Android. O APK roda a interface localmente em um WebView e usa Kotlin para receber compartilhamentos e abrir o seletor de arquivos do Android.

## Downloads

Abra a [release mais recente](https://github.com/daadvi/Save-It/releases/latest) e escolha o arquivo do seu dispositivo.

- **Windows:** baixe `Save-It.html`, guarde-o em uma pasta permanente e abra-o no navegador. Em **Criar atalho**, baixe o ZIP, extraia-o na pasta do HTML e mova `Save It!.url` para a Área de Trabalho. Deixe `Save-It.ico` junto do HTML. A imagem identifica o atalho e a aba do navegador. O Explorer pode exibir o ícone do navegador no próprio arquivo HTML.
- **Android 8.0 ou superior:** baixe e abra o APK pelo celular. Caso solicitado, autorize a instalação pelo navegador ou gerenciador usado no download.

O HTML funciona localmente por `file://`, inclusive sem conexão. As prévias externas precisam de internet. A coleção fica no armazenamento do navegador, associado ao caminho do arquivo. Use o mesmo navegador e exporte um backup JSON antes de mover o HTML ou limpar os dados. O backup também permite trocar a coleção com o Android.

## Usar no Android

- No Instagram ou em outro app, escolha **Compartilhar → Save It!** no menu do Android. Quando a publicação inclui um link, ele é salvo imediatamente no **Mural**, em **Sem pasta**. Não é preciso confirmar. Se o app enviar apenas um arquivo de vídeo, use a opção de compartilhar o link da publicação.
- Para cadastrar um endereço manualmente, toque em **+ Link**. O app busca a imagem do conteúdo. Sites que não fornecem prévia aparecem com o domínio.
- Em **Pastas**, crie uma pasta e entre nela para criar uma subpasta. Mantenha pressionado o nome de uma pasta para renomeá-la. O menu de cada marcador permite alterar a pasta, o título e a nota.
- Toque em **✓** para concluir um marcador. A aba **Concluídos** mantém o vínculo com a pasta e permite restaurá-lo.
- Em **Ajustes**, escolha a cor e importe ou exporte os favoritos pelo seletor de arquivos do Android. JSON preserva notas, hierarquia e conclusão. HTML segue o formato de favoritos dos navegadores.
- Para trazer a coleção da versão web, importe o backup JSON previamente exportado. Os dados ficam no armazenamento privado do app. A consulta de prévias e imagens requer acesso à internet.

## Importação JSON

São aceitos backups do Save It! com `bookmarks` e `folders`, e backups do Savr com `bookmarks` e `collections`. As coleções Savr usam `bookmarkUrls` para associar os links. A importação mantém títulos, descrições como notas, imagens e datas. Links sem coleção ficam em “Sem pasta”. A prévia informa a quantidade de registros, links inválidos e duplicados antes de confirmar.

Com as consultas externas ativadas, o app busca as descrições ausentes após importar JSON ou HTML, inclusive quando o marcador já tem miniatura. Elas aparecem nos cartões e podem ser pesquisadas. Para consultar novamente os links já salvos, use **Configurações → Atualizar prévias**.

Em **Configurações → Importar só os links**, selecione o JSON para procurar os endereços linha por linha, inclusive em backups Savr válidos. O app verifica o formato HTTP ou HTTPS e o domínio localmente, ignora miniaturas identificadas no backup e mostra os endereços únicos como marcadores no Mural.

Se a importação completa do JSON falhar, o Save It! procura endereços HTTP e HTTPS e apresenta a prévia **Somente links**. Ao confirmar, cada endereço recuperado vira um marcador pendente no Mural, em **Sem pasta**. Depois, você pode distribuir esses marcadores entre suas pastas.

O Save It! organiza cada marcador em uma pasta. Se um backup Savr associar o mesmo link a várias coleções, a prévia informa que será usada a primeira coleção do arquivo.

## Compilar

Abra o projeto no Android Studio com o SDK 35 instalado, ou execute com JDK 17 ou 21:

```sh
./gradlew testDebugUnitTest assembleDebug
```

O APK para testes fica em `app/build/outputs/apk/debug/app-debug.apk`.

O fluxo de conta, as regras do Firestore e a montagem do HTML e do APK com sincronização estão em [sync/README.md](sync/README.md).
