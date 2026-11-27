package br.com.saveit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.net.URI
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("collection", MODE_PRIVATE) }
    private var library = Library()
    private var tab = 0
    private var folderId: String? = null
    private var doneFolder: String? = null
    private var query = ""
    private var selectedType = 0
    private var sortOrder = 0
    private var selectedFolder: String? = null
    private var folderStatus = 0
    private var accent = Color.rgb(201, 183, 252)
    private lateinit var column: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var dock: LinearLayout
    private val previews = Executors.newFixedThreadPool(3)
    private val requested = mutableSetOf<String>()
    private var exportHtml = false
    private val destinations = listOf("Mural", "Pastas", "Concluídos", "Ajustes")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val raw = prefs.getString("library", null)
        if (raw != null) {
            try { library = Library.parseJson(raw) }
            catch (_: Exception) {
                AlertDialog.Builder(this).setMessage("Não foi possível abrir a coleção salva.").setPositiveButton("Fechar") { _, _ -> finish() }.setCancelable(false).show()
                return
            }
        }
        accent = prefs.getInt("accent", accent)
        tab = state?.getInt("tab") ?: 0
        folderId = state?.getString("folder")
        doneFolder = state?.getString("doneFolder")
        query = state?.getString("query").orEmpty()
        exportHtml = state?.getBoolean("html") ?: false
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets.consumeSystemWindowInsets()
            }
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(7), dp(18), 0) }
        frame.addView(column, LinearLayout.LayoutParams(-1, 0, 1f))
        dock = LinearLayout(this).apply { setPadding(dp(8), dp(7), dp(8), dp(7)); background = panel(Color.WHITE) }
        frame.addView(dock)
        setContentView(frame)
        if (state == null) receiveShare(intent)
        show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!::column.isInitialized) return
        setIntent(intent)
        receiveShare(intent)
        show()
    }

    private fun receiveShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val url = sharedWebUrl(intent)
        if (url == null) {
            message("Este compartilhamento veio sem um link. No aplicativo de origem, compartilhe o link da publicação.")
            return
        }
        if (library.links.any { it.url == url }) {
            message("Este link já está na sua coleção.")
            return
        }
        val bookmark = Link(freshId(), url, URI(url).host, null)
        save(library.copy(links = library.links + bookmark))
        tab = 0
        query = ""
        message("Link salvo no mural.")
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("tab", tab); out.putString("folder", folderId); out.putString("doneFolder", doneFolder)
        out.putString("query", query); out.putBoolean("html", exportHtml)
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun save(next: Library) {
        library = next
        prefs.edit().putString("library", next.json()).apply()
    }
    private fun path(id: String?) = library.path(id).joinToString(" / ") { it.name }
    private fun folderName(id: String?) = library.folders.find { it.id == id }?.name ?: "Sem pasta"
    private fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun LinearLayout.weighted(view: View, height: Int = -2) = addView(view, LinearLayout.LayoutParams(0, height, 1f))
    private fun select(title: String, names: List<String>, onSelect: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(names.toTypedArray()) { _, index -> onSelect(index) }.show()
    }
    private fun show() {
        column.removeAllViews()
        dock.removeAllViews()
        val navLabels = listOf("▦  Início", "▱  Pastas", "◉  Concluídos", "⚙  Configurações")
        val dockShell = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = outlined()
            elevation = dp(5).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        navLabels.forEachIndexed { index, title ->
            val item = text(title, 11, index == tab).apply {
                gravity = Gravity.CENTER
                background = if (index == tab) outlined(honey) else null
                setOnClickListener { tab = index; query = ""; show() }
            }
            dockShell.addView(item, LinearLayout.LayoutParams(0, dp(45), 1f))
        }
        dock.addView(dockShell, LinearLayout.LayoutParams(-1, -2))

        val brand = row().apply { setPadding(dp(1), dp(5), dp(1), dp(14)) }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.bookmark) }, LinearLayout.LayoutParams(dp(34), dp(34)))
        brand.addView(text("Save It!", 26, true).apply { setPadding(dp(7), 0, dp(9), 0) })
        brand.addView(text("│  Biblioteca local", 12, color = subtle), LinearLayout.LayoutParams(0, -2, 1f))
        brand.addView(text("●", 13, color = Color.rgb(65, 130, 70)))
        column.addView(brand)
        column.addView(View(this).apply { setBackgroundColor(ink) }, LinearLayout.LayoutParams(-1, dp(2)))
        column.addSpace(this, 25)
        val eyebrow = listOf("BIBLIOTECA LOCAL", "CADA IDEIA NO SEU LUGAR", "MISSÃO CUMPRIDA", "DEIXE COM A SUA CARA")[tab]
        column.addView(text("━  $eyebrow", 11, true).apply { letterSpacing = .13f })
        column.addSpace(this, 10)
        val heading = row()
        val parts = listOf(
            "Meu" to "mural.", "Um universo de" to "pastas.",
            "Visto. Lido." to "Concluído.", "Seu espaço." to "Suas regras."
        )[tab]
        heading.addView(text(parts.first + " ", 27, true))
        heading.addView(text(parts.second, 27, true).apply { setBackgroundColor(honey); setPadding(dp(4), 0, dp(4), 0) })
        column.addView(heading)
        column.addSpace(this, 10)
        val subtitle = listOf(
            "Seus links pendentes de todas as pastas.", "Organize suas descobertas. Do seu jeito.",
            "Suas descobertas já exploradas, sempre à mão.", "Pequenos ajustes para uma coleção que é sua."
        )[tab]
        column.addView(text(subtitle, 14, color = subtle))
        column.addSpace(this, 12)
        if (tab != 3) {
            column.addView(action("+ Salvar link", honey) { linkEditor() }, LinearLayout.LayoutParams(-1, dp(46)))
            column.addSpace(this, 16)
        }
        if (tab == 3) { settings(); return }
        if (tab == 1) {
            val newFolder = action("+ Nova pasta", Color.WHITE) { folderEditor() }
            column.addView(newFolder, LinearLayout.LayoutParams(-1, dp(45)))
            column.addSpace(this, 17)
            val folderBar = LinearLayout(this)
            if (folderId != null) folderBar.addView(action("‹ Voltar", Color.WHITE) {
                folderId = library.folders.find { it.id == folderId }?.parent; show()
            })
            library.folders.filter { it.parent == folderId }.forEach { f ->
                folderBar.addView(action("▱  " + f.name, honey) { folderId = f.id; show() }.apply {
                    setOnLongClickListener { folderEditor(f); true }
                })
            }
            if (folderId != null) column.addView(text(path(folderId), 14, true))
            column.addView(HorizontalScrollView(this).apply { addView(folderBar) })
            if (library.folders.isEmpty()) {
                val emptyFolders = emptyPanel("Sua primeira pasta está esperando", "Crie um lugar para suas próximas descobertas.")
                emptyFolders.addView(action("Nova pasta", honey) { folderEditor() })
                column.addView(emptyFolders, LinearLayout.LayoutParams(-1, -2))
            }
            val statuses = row()
            listOf("Pendentes", "Concluídos", "Todos").forEachIndexed { i, name ->
                statuses.addView(chip(name, folderStatus == i) { folderStatus = i; show() })
            }
            column.addView(statuses)
            column.addSpace(this, 12)
        } else {
            column.addView(text("${library.links.count { it.done == (tab == 2) }} links salvos    ·    ${library.folders.size} pastas", 13, color = subtle))
            column.addSpace(this, 17)
        }
        val searchRow = row()
        val search = action(if (query.isEmpty()) "⌕  Buscar nos seus links..." else "⌕  $query", Color.WHITE) {
            val input = EditText(this).apply { setSingleLine(); setText(query); hint = "Título ou endereço" }
            AlertDialog.Builder(this).setTitle("Buscar marcadores").setView(input)
                .setPositiveButton("Buscar") { _, _ -> query = input.text.toString().trim(); show() }
                .setNeutralButton("Limpar") { _, _ -> query = ""; show() }.setNegativeButton("Cancelar", null).show()
        }.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; setPadding(dp(11), 0, 0, 0) }
        searchRow.weighted(search, dp(44))
        val folders = listOf("Todas as pastas") + library.folders.map { path(it.id) }
        val folderFilter = action(if (selectedFolder == null) "Todas ▾" else folderName(selectedFolder), Color.WHITE) {
            select("Filtrar por pasta", folders) { selectedFolder = library.folders.getOrNull(it - 1)?.id; show() }
        }
        searchRow.addView(folderFilter, LinearLayout.LayoutParams(dp(115), dp(44)).apply { leftMargin = dp(7) })
        column.addView(searchRow)
        column.addSpace(this, 12)
        val chips = LinearLayout(this)
        listOf("▦ Todos", "♧ Sites", "▣ Vídeos", "▧ Imagens", "♧ Jogos").forEachIndexed { i, name ->
            chips.addView(chip(name, selectedType == i) { selectedType = i; show() }.apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
            })
        }
        column.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        column.addSpace(this, 17)
        val items = library.links.filter { b ->
            val folderMatch = when (tab) {
                1 -> b.folder == folderId
                2 -> doneFolder == null || library.path(b.folder).any { it.id == doneFolder }
                else -> selectedFolder == null || library.path(b.folder).any { it.id == selectedFolder }
            }
            val statusMatch = if (tab == 1) folderStatus == 2 || b.done == (folderStatus == 1) else b.done == (tab == 2)
            val kind = when (selectedType) {
                2 -> b.url.contains("youtube", true) || b.url.contains("vimeo", true) || b.url.contains("instagram", true)
                3 -> b.url.substringBefore('?').matches(Regex(".*\\.(png|jpg|jpeg|webp|gif)", RegexOption.IGNORE_CASE))
                4 -> b.url.contains("steampowered", true) || b.url.contains("itch.io", true)
                1 -> !b.url.contains("youtube", true) && !b.url.contains("instagram", true)
                else -> true
            }
            folderMatch && statusMatch && kind && (b.title.contains(query, true) || b.url.contains(query, true) || b.note.contains(query, true))
        }.sortedByDescending { if (tab == 2) it.completed else it.created }
        column.addView(text("SUAS DESCOBERTAS   ${items.size} marcadores ─────────", 11, true))
        column.addSpace(this, 10)
        if (items.isEmpty()) {
            val title = if (query.isNotEmpty()) "Nenhum link por aqui" else if (tab == 2) "Nada concluído ainda" else "Espaço para novas descobertas"
            val panel = emptyPanel(title, if (query.isNotEmpty()) "Tente outro termo ou ajuste os filtros." else "Salve um link e comece sua coleção.")
            if (tab != 2) panel.addView(action("+ Salvar link", honey) { linkEditor() })
            column.addView(panel, LinearLayout.LayoutParams(-1, -2))
            return
        }
        grid = RecyclerView(this).apply {
            layoutManager = StaggeredGridLayoutManager(if (resources.configuration.screenWidthDp >= 650) 3 else 2, RecyclerView.VERTICAL)
            adapter = Cards(items, library.folders.associateBy { it.id }, accent, ::openLink, { linkEditor(it) }, ::complete, ::resolve)
        }
        column.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun emptyPanel(title: String, description: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = outlined(dashed = true)
        setPadding(dp(16), dp(45), dp(16), dp(45))
        addView(text("✳", 33))
        addView(text(title, 20, true))
        addSpace(this@MainActivity, 8)
        addView(text(description, 13, color = subtle))
        addSpace(this@MainActivity, 17)
    }

    private fun openLink(link: Link) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }.onFailure { message("Nenhum navegador disponível.") }
    }
    private fun complete(link: Link) {
        save(library.copy(links = library.links.map {
            if (it.id == link.id) it.copy(done = !it.done, completed = if (it.done) 0L else System.currentTimeMillis()) else it
        }))
        show()
    }
    private fun resolve(item: Link) {
        if (!requested.add(item.id + item.url)) return
        previews.execute {
            val data = preview(item.url)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val current = library.links.find { it.id == item.id && it.url == item.url } ?: return@runOnUiThread
                val title = if (current.title == URI(item.url).host) data.title.ifBlank { current.title } else current.title
                if (current.image == data.image && current.title == title) return@runOnUiThread
                val updated = current.copy(image = data.image, title = title)
                save(library.copy(links = library.links.map { if (it.id == item.id) updated else it }))
                if (::grid.isInitialized) (grid.adapter as? Cards)?.update(updated)
            }
        }
    }
    private fun linkEditor(item: Link? = null) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        fun field(hintText: String, value: String) = EditText(this).apply {
            hint = hintText; setSingleLine(); setText(value); body.addView(this)
        }
        val url = field("URL do link", item?.url.orEmpty())
        val title = field("Título (opcional)", item?.title.orEmpty())
        val note = field("Nota (opcional)", item?.note.orEmpty())
        val folder = Spinner(this)
        val choices = listOf<Folder?>(null) + library.folders
        folder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { if (it == null) "Sem pasta" else path(it.id) })
        val selected = if (item != null) item.folder else if (tab == 1) folderId else null
        folder.setSelection(choices.indexOfFirst { it?.id == selected }.coerceAtLeast(0))
        body.addView(folder)
        val builder = AlertDialog.Builder(this).setTitle(if (item == null) "Salvar link" else "Editar marcador").setView(body)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null)
        if (item != null) builder.setNeutralButton("Excluir") { _, _ ->
            AlertDialog.Builder(this).setMessage("Excluir este marcador?").setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ -> save(library.copy(links = library.links.filterNot { it.id == item.id })); show() }.show()
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val safe = webUrl(url.text.toString())
            if (safe == null) { url.error = "Informe um endereço HTTP ou HTTPS."; return@setOnClickListener }
            if (library.links.any { it.url == safe && it.id != item?.id }) { url.error = "Este link já está salvo."; return@setOnClickListener }
            val b = (item ?: Link(freshId(), safe, "", null)).copy(url = safe, title = title.text.toString().trim().ifBlank { URI(safe).host },
                note = note.text.toString(), folder = choices[folder.selectedItemPosition]?.id, image = if (item?.url == safe) item.image else "")
            save(library.copy(links = library.links.filterNot { it.id == b.id } + b))
            dialog.dismiss(); show(); if (b.image.isBlank()) resolve(b)
        }
    }
    private fun folderEditor(existing: Folder? = null) {
        val name = EditText(this).apply { hint = "Nome da pasta"; setText(existing?.name.orEmpty()); setSingleLine() }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Nova pasta" else "Renomear pasta").setView(name)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString().trim()
            if (value.isEmpty()) { name.error = "Informe o nome"; return@setOnClickListener }
            val f = existing?.copy(name = value) ?: Folder(freshId(), value, folderId)
            save(library.copy(folders = library.folders.filterNot { it.id == f.id } + f)); dialog.dismiss(); show()
        }
    }
    private fun settings() {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val appearance = surface()
        appearance.addView(text("Aparência", 21, true))
        appearance.addSpace(this, 9)
        appearance.addView(text("Uma cor para acompanhar suas descobertas.", 13, color = subtle))
        appearance.addSpace(this, 14)
        val colors = listOf("#C9B7FC", "#FFD17E", "#BCE5C8", "#FFB6CB", "#A9D9FA")
        val colorRow = row()
        colors.forEach { value ->
            val color = Color.parseColor(value)
            colorRow.addView(text(if (accent == color) "✓" else "", 18, true).apply {
                gravity = Gravity.CENTER; background = outlined(color)
                setOnClickListener { accent = color; prefs.edit().putInt("accent", accent).apply(); show() }
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        }
        appearance.addView(colorRow)
        appearance.addSpace(this, 15)
        appearance.addView(text("Tema", 13, true))
        appearance.addView(action("Claro  ▾", Color.WHITE) { message("Tema claro selecionado") })
        appearance.addSpace(this, 12)
        appearance.addView(text("Tamanho dos cards", 13, true))
        appearance.addView(action(if (prefs.getBoolean("compact", false)) "Compacto  ▾" else "Confortável  ▾", Color.WHITE) {
            select("Tamanho dos cards", listOf("Confortável", "Compacto")) {
                prefs.edit().putBoolean("compact", it == 1).apply(); show()
            }
        })
        list.addView(appearance, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val portable = surface()
        portable.addView(text("Seus links vão com você", 21, true))
        portable.addSpace(this, 9)
        portable.addView(text("Importe favoritos do navegador ou restaure um backup. O JSON preserva pastas, notas e conclusão.", 13, color = subtle))
        portable.addSpace(this, 16)
        portable.addView(action("Importar JSON / HTML", honey) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
        })
        portable.addSpace(this, 11)
        val exports = row()
        exports.weighted(action("Exportar JSON", Color.WHITE) { export(false) }, dp(45))
        exports.weighted(action("Exportar HTML", Color.WHITE) { export(true) }, dp(45))
        portable.addView(exports)
        list.addView(portable, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val previewsPanel = surface()
        previewsPanel.addView(text("Prévias automáticas", 21, true))
        previewsPanel.addSpace(this, 9)
        previewsPanel.addView(text("As imagens dos links são consultadas quando há internet. Quando não há prévia, o domínio aparece no card.", 13, color = subtle))
        list.addView(previewsPanel)
    }
    private fun export(html: Boolean) {
        exportHtml = html
        val ext = if (html) "html" else "json"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (html) "text/html" else "application/json").putExtra(Intent.EXTRA_TITLE, "save-it.$ext"), 2)
    }
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        val uri = data?.data ?: return
        if (result != RESULT_OK) return
        val snapshot = library
        val html = exportHtml
        Thread {
            runCatching {
                if (code == 2) {
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(if (html) snapshot.html() else snapshot.json()) }
                    runOnUiThread { message("Arquivo exportado.") }
                } else if (code == 1) {
                    val text = requireNotNull(contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                        val buf = CharArray(8192)
                        buildString {
                            while (true) {
                                val n = reader.read(buf)
                                if (n < 0) break
                                require(length + n <= 12_000_000) { "Arquivo muito grande" }
                                append(buf, 0, n)
                            }
                        }.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                    }
                    val incoming = if (text.startsWith("{")) Library.parseJson(text) else Library.parseHtml(text)
                    runOnUiThread {
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        AlertDialog.Builder(this).setTitle("Importar favoritos?")
                            .setMessage("${incoming.links.size} links e ${incoming.folders.size} pastas. Links repetidos serão ignorados.")
                            .setNegativeButton("Cancelar", null).setPositiveButton("Importar") { _, _ ->
                                save(library.merge(incoming)); tab = 0; query = ""; show(); message("Importação concluída.")
                            }.show()
                    }
                }
            }.onFailure { runOnUiThread { message("Não foi possível processar o arquivo: " + it.localizedMessage.orEmpty()) } }
        }.start()
    }
    override fun onDestroy() { previews.shutdownNow(); super.onDestroy() }
}
