# Save It!

Aplicativo Android para guardar links em um mural visual. A interface usa Kotlin, Android Views e RecyclerView com colunas escalonadas. Requer Android 8.0 ou superior.

## Instalar

Baixe o APK na seção [Releases](https://github.com/daadvi/Save-It/releases) pelo celular e abra o arquivo. Se o Android solicitar, autorize a instalação pelo navegador ou gerenciador usado para o download.

## Usar a coleção

- Em **Mural**, toque em **+ Link** ou use **Compartilhar → Save It!** a partir de outro aplicativo. O app busca a imagem do conteúdo. Sites que não fornecem prévia aparecem com o domínio.
- Em **Pastas**, crie uma pasta e entre nela para criar uma subpasta. Mantenha pressionado o nome de uma pasta para renomeá-la. O menu de cada marcador permite alterar a pasta, o título e a nota.
- Toque em **✓** para concluir um marcador. A aba **Concluídos** mantém o vínculo com a pasta e permite restaurá-lo.
- Em **Ajustes**, escolha a cor e importe ou exporte os favoritos pelo seletor de arquivos do Android. JSON preserva notas, hierarquia e conclusão. HTML segue o formato de favoritos dos navegadores.
- Para trazer a coleção da versão web, importe o backup JSON previamente exportado. Os dados ficam no armazenamento privado do app. A consulta de prévias e imagens requer acesso à internet.

## Compilar

Abra o projeto no Android Studio com o SDK 35 instalado, ou execute com JDK 17 ou 21:

```sh
./gradlew testDebugUnitTest assembleDebug
```

O APK para testes fica em `app/build/outputs/apk/debug/app-debug.apk`.
