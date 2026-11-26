package br.com.saveit

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
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

    private fun library(activity: Activity): Library {
        val raw = activity.getSharedPreferences("collection", Context.MODE_PRIVATE).getString("library", null)
        return raw?.let(Library::parseJson) ?: Library()
    }

    @Test fun selectingShareTargetSavesOnceWithoutPressingSave() {
        val intent = textShare("Olha este vídeo!\n$reel")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        val activity = controller.get()
        val link = library(activity).links.single()
        assertEquals(reel, link.url)
        assertNull(link.folder)
        assertFalse(link.done)

        controller.newIntent(textShare(reel))
        assertEquals(listOf(link), library(activity).links)

        val other = "https://example.org/segundo"
        controller.newIntent(textShare(other))
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(setOf(reel, other), library(reopened.get()).links.map { it.url }.toSet())
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

    @Test fun fileOnlyShareDoesNotSaveAPlaceholder() {
        val onlyFile = Intent(Intent.ACTION_SEND).setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/12"))
        val controller = Robolectric.buildActivity(MainActivity::class.java, onlyFile).setup()
        assertTrue(library(controller.get()).links.isEmpty())
        controller.pause().stop().destroy()
    }
}
