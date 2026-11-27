package br.com.saveit

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

val honey = Color.rgb(255, 207, 113)
val subtle = Color.rgb(103, 103, 96)

fun Context.outlined(color: Int = Color.WHITE, dashed: Boolean = false): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(6).toFloat()
        if (dashed) setStroke(dp(2), Color.rgb(165, 165, 155), dp(7).toFloat(), dp(5).toFloat())
        else setStroke(dp(2), ink)
    }

fun Context.text(value: String, size: Int, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
    text = value
    textSize = size.toFloat()
    setTextColor(color)
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    includeFontPadding = false
}

fun Context.chip(value: String, active: Boolean, onClick: () -> Unit) = text(value, 13, active,
    if (active) Color.WHITE else ink).apply {
    gravity = Gravity.CENTER
    background = outlined(if (active) ink else Color.WHITE)
    setPadding(dp(13), dp(7), dp(13), dp(7))
    setOnClickListener { onClick() }
}

fun Context.surface(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = outlined()
    elevation = dp(4).toFloat()
    setPadding(dp(18), dp(18), dp(18), dp(18))
}

fun LinearLayout.addSpace(context: Context, height: Int) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(height)))
}
