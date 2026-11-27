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
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(7), dp(12), 0) }
        frame.addView(column, LinearLayout.LayoutParams(-1, 0, 1f))
        dock = LinearLayout(this).apply { setPadding(dp(4), dp(4), dp(4), dp(4)); setBackgroundColor(ink) }
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
        destinations.forEachIndexed { index, text ->
            dock.weighted(action(text, if (index == tab) accent else paper) {
                tab = index; query = ""; show()
            }.apply { textSize = 12f }, dp(48))
        }
        val header = row()
        header.addView(ImageView(this).apply { setImageResource(br.com.saveit.R.drawable.bookmark) }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.weighted(label("Save It!", 34, true))
        header.addView(action("+ Link", accent) { linkEditor() })
        column.addView(header)
        column.addView(label(destinations[tab], 24, true))
        if (tab == 3) { settings(); return }
        val tools = row()
        tools.weighted(action(query.ifBlank { "Buscar" }, Color.WHITE) {
            val input = EditText(this).apply { setSingleLine(); setText(query); hint = "Título ou endereço" }
            AlertDialog.Builder(this).setTitle("Buscar marcadores").setView(input)
                .setPositiveButton("Buscar") { _, _ -> query = input.text.toString().trim(); show() }
                .setNeutralButton("Limpar") { _, _ -> query = ""; show() }.setNegativeButton("Cancelar", null).show()
        })
        if (tab == 1) {
            tools.weighted(action("+ Pasta", accent) { folderEditor() })
        } else if (tab == 2) {
            tools.weighted(action(if (doneFolder == null) "Todas as pastas" else folderName(doneFolder), accent) {
                select("Concluídos por pasta", listOf("Todas as pastas") + library.folders.map { path(it.id) }) {
                    doneFolder = library.folders.getOrNull(it - 1)?.id; show()
                }
            })
        }
        column.addView(tools)
        if (tab == 1) {
            val folderBar = LinearLayout(this)
            if (folderId != null) folderBar.addView(action("‹ Voltar", Color.WHITE) {
                folderId = library.folders.find { it.id == folderId }?.parent; show()
            })
            library.folders.filter { it.parent == folderId }.forEach { f ->
                folderBar.addView(action("▰  " + f.name, accent) { folderId = f.id; show() }.apply {
                    setOnLongClickListener { folderEditor(f); true }
                })
            }
            if (folderId != null) column.addView(label(path(folderId), 14, true))
            column.addView(HorizontalScrollView(this).apply { addView(folderBar) })
        }
        val items = library.links.filter { b ->
            val inFolder = when (tab) {
                1 -> b.folder == folderId
                2 -> doneFolder == null || library.path(b.folder).any { it.id == doneFolder }
                else -> true
            }
            b.done == (tab == 2) && inFolder && (b.title.contains(query, true) || b.url.contains(query, true) || b.note.contains(query, true))
        }.sortedByDescending { if (tab == 2) it.completed else it.created }
        if (items.isEmpty()) {
            val empty = label(when {
                query.isNotEmpty() -> "Nenhum resultado para “$query”."
                tab == 2 -> "Os links que você concluir aparecerão aqui."
                else -> "Salve seu primeiro link com + Link."
            }, 17).apply { gravity = Gravity.CENTER }
            column.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
            return
        }
        grid = RecyclerView(this).apply {
            layoutManager = StaggeredGridLayoutManager(if (resources.configuration.screenWidthDp >= 650) 3 else 2, RecyclerView.VERTICAL)
            adapter = Cards(items, library.folders.associateBy { it.id }, accent, ::openLink, { linkEditor(it) }, ::complete, ::resolve)
        }
        column.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
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
        column.addView(ScrollView(this).apply { addView(list) })
        list.addView(label("Cor de destaque", 18, true))
        val names = listOf("Lilás", "Menta", "Mel", "Rosa")
        val colors = listOf("#C9B7FC", "#ACDFBE", "#FFD278", "#FFB7CA")
        val colorsRow = row()
        names.forEachIndexed { i, name -> colorsRow.weighted(action(name, Color.parseColor(colors[i])) {
            accent = Color.parseColor(colors[i]); prefs.edit().putInt("accent", accent).apply(); show()
        }) }
        list.addView(colorsRow)
        list.addView(label("Coleção neste dispositivo", 18, true))
        list.addView(action("Importar JSON ou HTML", accent) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
        })
        list.addView(action("Exportar backup JSON", Color.WHITE) { export(false) })
        list.addView(action("Exportar favoritos HTML", Color.WHITE) { export(true) })
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
