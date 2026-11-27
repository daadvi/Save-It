package br.com.saveit

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.FrameLayout
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("collection", MODE_PRIVATE) }
    private lateinit var web: WebView
    private var pageReady = false
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val selectedDocument = AtomicReference<Pair<String, Uri>?>(null)
    private var exporting = false
    private val pendingExport by lazy { File(cacheDir, "pending-backup") }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        exporting = state?.getBoolean("exporting") == true
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(247, 247, 242))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            // Use the same CSS viewport and text scale as the file opened in Chrome.
            settings.textZoom = 100
            webViewClient = pageClient()
            webChromeClient = fileClient()
        }
        // Insets belong to the container. WebView padding does not resize the CSS viewport.
        val viewport = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(247, 247, 242))
            setOnApplyWindowInsetsListener { container, insets ->
                applySafeArea(container, insets)
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 30) WindowInsets.CONSUMED
                else insets.consumeSystemWindowInsets().consumeStableInsets()
            }
            addView(web, FrameLayout.LayoutParams(-1, -1))
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(viewport)
        viewport.requestApplyInsets()
        if (state == null) receiveShare(intent)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "SaveItAndroid", setOf(ORIGIN)) { _, message, _, mainFrame, reply ->
                if (!mainFrame) return@addWebMessageListener
                val data = runCatching { JSONObject(message.data ?: "") }.getOrNull()
                    ?: return@addWebMessageListener
                when (data.optString("action")) {
                    "ready" -> {
                        pageReady = true
                        reply.postMessage(bootstrap().toString())
                    }
                    "migrated" -> prefs.edit().putBoolean("htmlMigrationComplete", true).apply()
                    "shared" -> acknowledgeShare(data.optString("url"))
                    "export" -> exportBackup(data)
                    "open" -> openLink(Uri.parse(data.optString("url")))
                }
            }
        }
        web.loadUrl(APP_URL)
    }

    internal fun applySafeArea(container: View, insets: WindowInsets) {
        val safe = safeInsets(insets)
        container.setPadding(safe[0], safe[1], safe[2], safe[3])
    }

    internal fun safeInsets(insets: WindowInsets): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or
                WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            return intArrayOf(safe.left, safe.top, safe.right, safe.bottom)
        }
        @Suppress("DEPRECATION")
        val safe = intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        if (Build.VERSION.SDK_INT >= 28) {
            insets.displayCutout?.let { cutout ->
                safe[0] = maxOf(safe[0], cutout.safeInsetLeft)
                safe[1] = maxOf(safe[1], cutout.safeInsetTop)
                safe[2] = maxOf(safe[2], cutout.safeInsetRight)
                safe[3] = maxOf(safe[3], cutout.safeInsetBottom)
            }
        }
        return safe
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
        if (pageReady) {
            web.evaluateJavascript("window.dispatchEvent(new Event('save-it-android-resume'))", null)
        }
    }

    private fun pendingShares(): JSONArray = JSONArray(prefs.getString("pendingShares", "[]") ?: "[]")

    private fun receiveShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val url = sharedWebUrl(intent)
        if (url == null) {
            toast("Este compartilhamento veio sem um link. Compartilhe o link da publicação.")
            return
        }
        val pending = pendingShares()
        val edit = prefs.edit()
        if ((0 until pending.length()).none { pending.optString(it) == url }) {
            edit.putString("pendingShares", pending.put(url).toString())
        }
        if (intent.type?.startsWith("video/") == true) {
            edit.putStringSet("pendingVideos", prefs.getStringSet("pendingVideos", emptySet()).orEmpty() + url)
        }
        edit.apply()
    }

    private fun acknowledgeShare(url: String) {
        val pending = pendingShares()
        val remaining = JSONArray()
        for (index in 0 until pending.length()) {
            val item = pending.getString(index)
            if (item != url) remaining.put(item)
        }
        prefs.edit().putString("pendingShares", remaining.toString())
            .putStringSet("pendingVideos", prefs.getStringSet("pendingVideos", emptySet()).orEmpty() - url)
            .apply()
    }

    private fun bootstrap(): JSONObject = JSONObject().put("shared", pendingShares()).apply {
        put("videos", JSONArray(prefs.getStringSet("pendingVideos", emptySet()).orEmpty().toList()))
        if (!prefs.getBoolean("htmlMigrationComplete", false)) {
            val legacy = prefs.getString("library", null)
            put("legacy", legacy?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject())
            if (prefs.contains("accent")) {
                put("accent", "#%06x".format(prefs.getInt("accent", 0) and 0xffffff))
            }
            put("compact", prefs.getBoolean("compact", false))
        }
    }

    private fun pageClient() = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.url.toString().startsWith("$ORIGIN/import/")) {
                return openSelectedDocument(request)
            }
            if (request.url.toString() != APP_URL) return null
            return WebResourceResponse("text/html", "UTF-8", 200, "OK",
                mapOf("Content-Security-Policy" to "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob: https:; connect-src 'self' https://api.microlink.io; form-action 'none'; frame-ancestors 'none'; base-uri 'none'"),
                assets.open("Save-It.html"))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.buildUpon().fragment(null).build().toString() == APP_URL) return false
            if (request.isForMainFrame && request.hasGesture()) openLink(url)
            return true
        }
    }

    // Called on WebView's request thread. Only the document chosen in the system picker is served.
    private fun openSelectedDocument(request: WebResourceRequest): WebResourceResponse {
        val document = selectedDocument.get()
        if (request.method == "GET" && document != null &&
            request.url.toString() == "$ORIGIN/import/${document.first}" &&
            selectedDocument.compareAndSet(document, null)) {
            val uri = document.second
            val name = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "backup"
            val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
            if (stream != null) {
                return WebResourceResponse("application/octet-stream", null, 200, "OK",
                    mapOf("Cache-Control" to "no-store", "X-Document-Name" to Uri.encode(name)), stream)
            }
        }
        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
    }

    private fun openLink(url: Uri) {
        if (url.scheme !in setOf("http", "https") || url.host.isNullOrEmpty()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
            .onFailure { toast("Nenhum navegador disponível.") }
    }

    private fun fileClient() = object : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
        ): Boolean {
            chooser?.onReceiveValue(null)
            chooser = callback
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            @Suppress("DEPRECATION")
            val launched = runCatching { startActivityForResult(picker, IMPORT_REQUEST) }.isSuccess
            if (!launched) {
                chooser = null
                callback.onReceiveValue(null)
                toast("Não foi possível abrir o seletor de arquivos.")
            }
            return true
        }
    }

    private fun exportBackup(data: JSONObject) {
        if (exporting) return
        val type = data.optString("type")
        if (type !in setOf("application/json", "text/html")) return
        if (runCatching { pendingExport.writeText(data.getString("content")) }.isFailure) {
            toast("Não foi possível preparar o backup.")
            return
        }
        exporting = true
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type).putExtra(Intent.EXTRA_TITLE, data.optString("name"))
        @Suppress("DEPRECATION")
        runCatching { startActivityForResult(picker, EXPORT_REQUEST) }.onFailure {
            exporting = false
            pendingExport.delete()
            toast("Não foi possível abrir o seletor de arquivos.")
        }
    }

    @Deprecated("WebView file chooser uses activity result callbacks")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        val uri = data?.data?.takeIf { result == RESULT_OK }
        when (request) {
            IMPORT_REQUEST -> {
                chooser?.onReceiveValue(null)
                chooser = null
                if (uri?.scheme == "content") {
                    val token = UUID.randomUUID().toString()
                    selectedDocument.set(token to uri)
                    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('save-it-document', {detail:'$token'}))", null)
                } else if (uri != null) {
                    toast("Escolha o arquivo pelo seletor de documentos do Android.")
                }
            }
            EXPORT_REQUEST -> {
                exporting = false
                if (uri != null) {
                    runCatching {
                        pendingExport.inputStream().use { backup ->
                            checkNotNull(contentResolver.openOutputStream(uri)).use { backup.copyTo(it) }
                        }
                    }.onSuccess { toast("Arquivo exportado.") }
                        .onFailure { toast("Não foi possível exportar o arquivo.") }
                }
                pendingExport.delete()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("exporting", exporting)
    }

    override fun onDestroy() {
        chooser?.onReceiveValue(null)
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val ORIGIN = "https://appassets.androidplatform.net"
        private const val APP_URL = "$ORIGIN/Save-It.html"
        private const val IMPORT_REQUEST = 1
        private const val EXPORT_REQUEST = 2
    }
}
