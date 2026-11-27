package br.com.saveit

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceTest {
    private fun allViews(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { allViews(root.getChildAt(it)) } else emptyList()

    private fun click(root: View, label: String) {
        val target = allViews(root).filterIsInstance<TextView>().first { it.text.toString().contains(label) }
        assertTrue(target.performClick())
    }

    @Test fun changingColorUpdatesHeadingButtonAndDockAndSurvivesRestart() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        click(activity.window.decorView, "Ajustes")
        val pink = Color.parseColor("#FFB6CB")
        val swatch = allViews(activity.window.decorView).filterIsInstance<TextView>()
            .first { (it.background as? GradientDrawable)?.color?.defaultColor == pink }
        assertTrue(swatch.performClick())
        assertEquals(pink, activity.getSharedPreferences("collection", Context.MODE_PRIVATE).getInt("accent", 0))
        click(activity.window.decorView, "Início")
        val heading = allViews(activity.window.decorView).filterIsInstance<TextView>().first { it.text == "mural." }
        assertEquals(pink, (heading.background as android.graphics.drawable.ColorDrawable).color)
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(MainActivity::class.java).setup()
        val again = allViews(reopened.get().window.decorView).filterIsInstance<TextView>().first { it.text == "mural." }
        assertEquals(pink, (again.background as android.graphics.drawable.ColorDrawable).color)
        reopened.pause().stop().destroy()
    }
}
