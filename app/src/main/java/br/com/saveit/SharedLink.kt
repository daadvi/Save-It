package br.com.saveit

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import org.jsoup.Jsoup

private fun linkInText(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val links = Patterns.WEB_URL.matcher(text)
    var bareLink: String? = null
    while (links.find()) {
        val start = links.start()
        if (start > 0 && text[start - 1] in "@:/") continue
        var candidate = links.group().trimEnd('.', ',', '!', ';', ':')
        while (candidate.endsWith(')') && candidate.count { it == ')' } > candidate.count { it == '(' }) {
            candidate = candidate.dropLast(1)
        }
        val url = webUrl(candidate) ?: continue
        if (candidate.startsWith("https://", true) || candidate.startsWith("http://", true)) return url
        if (bareLink == null) bareLink = url
    }
    return bareLink
}

private fun htmlLink(html: String?): String? {
    if (html.isNullOrBlank()) return null
    val doc = Jsoup.parse(html)
    return doc.select("a[href]").firstNotNullOfOrNull { anchor ->
        anchor.attr("href").takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }?.let(::webUrl)
    } ?: linkInText(doc.text())
}

@Suppress("DEPRECATION")
internal fun sharedWebUrl(intent: Intent): String? {
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
    val inText = if (intent.type == "text/html") htmlLink(text?.toString()) else linkInText(text)
    if (inText != null) return inText
    htmlLink(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))?.let { return it }
    val clip = intent.clipData
    if (clip != null) {
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            (item.uri?.toString()?.let(::webUrl) ?: linkInText(item.text) ?: htmlLink(item.htmlText))?.let { return it }
        }
    }
    val stream = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
    return intent.dataString?.let(::webUrl) ?: stream?.toString()?.let(::webUrl)
}
