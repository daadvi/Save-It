package br.com.saveit

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareFlowTest {
    private val reel = "https://www.instagram.com/reel/Ab_C123/?igsh=abc"
    private fun textShare(text: String) = Intent(Intent.ACTION_SEND)
        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun pending(activity: Activity): List<String> {
        val raw = activity.getSharedPreferences("collection", Context.MODE_PRIVATE)
            .getString("pendingShares", "[]")
        val urls = JSONArray(raw)
        return (0 until urls.length()).map { urls.getString(it) }
    }

    @Test fun sharedLinksWaitForThePageAndSurviveRestart() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, textShare("Olha este vídeo!\n$reel")).setup()
        val activity = controller.get()
        assertEquals(listOf(reel), pending(activity))
        controller.newIntent(textShare(reel))
        assertEquals(listOf(reel), pending(activity))
        val other = "https://example.org/segundo"
        controller.newIntent(textShare(other))
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(listOf(reel, other), pending(reopened.get()))
        reopened.pause().stop().destroy()
    }

    @Test fun videoWithClipUrlAndHtmlSharesResolveToWebLinks() {
        val intent = Intent(Intent.ACTION_SEND).setType("video/mp4").apply {
            clipData = ClipData.newRawUri("Publicação", Uri.parse(reel))
        }
        assertEquals(reel, sharedWebUrl(intent))
        assertEquals(reel, sharedWebUrl(Intent(Intent.ACTION_SEND).setType("text/html")
            .putExtra(Intent.EXTRA_TEXT, "<a href='$reel'>Abrir vídeo</a>")))
    }

    @Test fun captionOrSubjectWithAPublicationUrlIsAcceptedAlongsideTheVideo() {
        val video = Intent(Intent.ACTION_SEND).setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/12"))
            .putExtra(Intent.EXTRA_TEXT, "Um vídeo para guardar")
            .putExtra(Intent.EXTRA_SUBJECT, reel)
        val controller = Robolectric.buildActivity(MainActivity::class.java, video).setup()
        assertEquals(listOf(reel), pending(controller.get()))
        controller.pause().stop().destroy()
    }

    @Test fun fileOnlyShareDoesNotSaveAPlaceholder() {
        val onlyFile = Intent(Intent.ACTION_SEND).setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/12"))
        val controller = Robolectric.buildActivity(MainActivity::class.java, onlyFile).setup()
        assertTrue(pending(controller.get()).isEmpty())
        controller.pause().stop().destroy()
    }
}
