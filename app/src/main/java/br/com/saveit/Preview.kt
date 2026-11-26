package br.com.saveit

import org.jsoup.Jsoup
import java.net.URI

data class Preview(val title: String, val image: String)
fun preview(url: String): Preview {
    val uri = URI(url)
    val domain = uri.host ?: return Preview("", "")
    if (uri.path.orEmpty().substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif"))
        return Preview(domain, url)
    val videoId = when (domain.removePrefix("www.")) {
        "youtu.be" -> uri.path.trim('/')
        "youtube.com", "m.youtube.com" -> uri.query.orEmpty().split('&').find { it.startsWith("v=") }?.substringAfter('=') ?: uri.path.substringAfterLast('/')
        else -> null
    }
    val videoImage = videoId?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
    return runCatching {
        val response = Jsoup.connect(url).userAgent("Mozilla/5.0 (Android) SaveIt/1.0")
            .timeout(8000).maxBodySize(512 * 1024).ignoreContentType(true).execute()
        if (response.contentType().orEmpty().startsWith("image/")) return Preview(domain, url)
        val doc = response.parse()
        val image = doc.selectFirst("meta[property=og:image], meta[name=twitter:image], meta[property=twitter:image]")?.absUrl("content").orEmpty()
        Preview(doc.title().ifBlank { domain }, webUrl(image) ?: videoImage)
    }.getOrDefault(Preview(domain, videoImage))
}
