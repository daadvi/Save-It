package br.com.saveit

import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsetsTest {
    @Test fun webViewportFitsBetweenSystemBarsAndCutouts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val actualHost = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as FrameLayout
        assertTrue(actualHost.getChildAt(0) is WebView)
        val host = FrameLayout(activity)
        val web = WebView(activity)
        host.addView(web, FrameLayout.LayoutParams(-1, -1))
        // Test three-button navigation, gestures, and landscape with a side cutout.
        for (safe in listOf(Insets.of(0, 86, 0, 96), Insets.of(0, 86, 0, 28), Insets.of(90, 24, 48, 24))) {
            val insets = WindowInsets.Builder()
                .setSystemWindowInsets(safe)
                .setInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(), safe)
                .setVisible(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(), true).build()
            assertArrayEquals(intArrayOf(safe.left, safe.top, safe.right, safe.bottom), activity.safeInsets(insets))
            activity.applySafeArea(host, insets)
            host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 1080, 2400)
            assertEquals(safe.left, host.paddingLeft)
            assertEquals(safe.top, host.paddingTop)
            assertEquals(safe.right, host.paddingRight)
            assertEquals(safe.bottom, host.paddingBottom)
            assertEquals(0, web.paddingTop)
            assertEquals(0, web.paddingBottom)
        }
        controller.pause().stop().destroy()
    }
}
