package br.com.saveit

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.UUID

fun freshId(): String = UUID.randomUUID().toString()
fun webUrl(raw: String): String? = runCatching {
    val value = raw.trim()
    val uri = URI(if (value.contains(":")) value else "https://$value")
    require(uri.scheme.lowercase() in setOf("https", "http") && uri.host != null && uri.userInfo == null)
    uri.toASCIIString()
}.getOrNull()

data class Folder(val id: String, val name: String, val parent: String?)
data class Link(val id: String, val url: String, val title: String, val folder: String?,
                val image: String = "", val done: Boolean = false, val note: String = "",
                val created: Long = System.currentTimeMillis(), val completed: Long = 0)
data class Library(val folders: List<Folder> = emptyList(), val links: List<Link> = emptyList()) {
    fun path(id: String?): List<Folder> {
        val byId = folders.associateBy { it.id }
        return generateSequence(id?.let(byId::get)) { it.parent?.let(byId::get) }.toList().asReversed()
    }

    fun merge(other: Library): Library {
        val mapping = other.folders.associate { it.id to freshId() }
        val known = links.mapTo(mutableSetOf()) { it.url }
        val added = other.links.filter { known.add(it.url) }.map { it.copy(id = freshId(), folder = mapping[it.folder]) }
        return Library(folders + other.folders.map { it.copy(id = mapping.getValue(it.id), parent = mapping[it.parent]) }, links + added)
    }

    fun json(): String {
        val directories = JSONArray()
        folders.forEach { f -> directories.put(JSONObject().put("id", f.id).put("name", f.name).put("parentId", f.parent ?: JSONObject.NULL)) }
        val items = JSONArray()
        links.forEach { b -> items.put(JSONObject().put("id", b.id).put("url", b.url).put("title", b.title)
            .put("image", b.image).put("done", b.done).put("note", b.note).put("folderId", b.folder ?: JSONObject.NULL)
            .put("createdAt", b.created).put("completedAt", b.completed)) }
        return JSONObject().put("version", 1).put("folders", directories).put("bookmarks", items).toString()
    }

    fun html(): String = buildString {
        fun escaped(text: String) = org.jsoup.nodes.Entities.escape(text).replace("\"", "&quot;")
        fun appendLevel(parent: String?) {
            appendLine("<DL><p>")
            folders.filter { it.parent == parent }.forEach {
                appendLine("<DT><H3>${escaped(it.name)}</H3>")
                appendLevel(it.id)
            }
            links.filter { it.folder == parent }.forEach {
                appendLine("<DT><A HREF=\"${escaped(it.url)}\" ADD_DATE=\"${it.created / 1000}\">${escaped(it.title)}</A>")
            }
            appendLine("</DL><p>")
        }
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1><META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\"><TITLE>Save It!</TITLE>")
        appendLevel(null)
    }

    companion object {
        fun parseJson(text: String): Library {
            fun JSONObject.optional(key: String) = if (isNull(key)) null else getString(key)
            fun JSONArray.rows() = (0 until length()).map(::getJSONObject)
            val input = JSONObject(text)
            val folders = input.getJSONArray("folders").rows().map {
                Folder(it.getString("id"), it.getString("name"), it.optional("parentId"))
            }
            val byId = folders.associateBy { it.id }
            require(byId.size == folders.size)
            folders.forEach { f ->
                val seen = mutableSetOf(f.id)
                var parent = f.parent
                while (parent != null) {
                    require(seen.add(parent) && seen.size <= 100 && parent in byId) { "Hierarquia inválida" }
                    parent = byId.getValue(parent).parent
                }
            }
            val links = input.getJSONArray("bookmarks").rows().mapNotNull { item ->
                val url = webUrl(item.optString("url")) ?: return@mapNotNull null
                Link(item.optString("id").ifBlank(::freshId), url, item.optString("title").ifBlank { URI(url).host },
                    item.optional("folderId")?.takeIf { it in byId }, webUrl(item.optString("image")) ?: "",
                    item.optBoolean("done"), item.optString("note"), item.optLong("createdAt", System.currentTimeMillis()), item.optLong("completedAt"))
            }
            return Library(folders, links)
        }

        fun parseHtml(text: String): Library {
            val root = Jsoup.parse(text).selectFirst("dl") ?: error("Lista de favoritos ausente")
            val folders = mutableListOf<Folder>()
            val links = mutableListOf<Link>()
            fun walk(element: Element, parent: String?, depth: Int) {
                require(depth <= 100)
                var next = parent
                for (child in element.children()) when (child.normalName()) {
                    "h3" -> { val f = Folder(freshId(), child.text(), parent); folders += f; next = f.id }
                    "a" -> webUrl(child.attr("href"))?.let { links += Link(freshId(), it, child.text().ifBlank { URI(it).host }, parent) }
                    "dl" -> { walk(child, next, depth + 1); next = parent }
                    "dt", "p" -> {
                        val heading = child.children().firstOrNull { it.normalName() == "h3" }
                        walk(child, parent, depth + 1)
                        next = if (heading != null) folders.lastOrNull { it.parent == parent && it.name == heading.text() }?.id else parent
                    }
                }
            }
            walk(root, null, 0)
            return Library(folders, links)
        }
    }
}
