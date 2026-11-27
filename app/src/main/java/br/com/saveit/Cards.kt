package br.com.saveit

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import coil.load
import java.net.URI

val ink = Color.rgb(30, 29, 32)
val paper = Color.rgb(248, 246, 237)
fun Context.dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
fun Context.panel(color: Int) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(5).toFloat()
    setStroke(dp(2), ink)
}
fun Context.label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size.toFloat()
    setTextColor(ink)
    if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
}
fun Context.action(value: String, color: Int, block: () -> Unit) = Button(this).apply {
    text = value
    isAllCaps = false
    setTextColor(ink)
    background = panel(color)
    elevation = dp(3).toFloat()
    setPadding(dp(7), 0, dp(7), 0)
    minimumWidth = 0
    minWidth = 0
    setOnClickListener { block() }
}

class Cards(
    initialItems: List<Link>, private val folders: Map<String, Folder>, private val accent: Int,
    private val open: (Link) -> Unit, private val edit: (Link) -> Unit,
    private val toggle: (Link) -> Unit, private val requestPreview: (Link) -> Unit
) : RecyclerView.Adapter<Cards.Holder>() {
    private val items = initialItems.toMutableList()
    fun update(link: Link) {
        val index = items.indexOfFirst { it.id == link.id }
        if (index >= 0) { items[index] = link; notifyItemChanged(index) }
    }
    class Holder(val box: LinearLayout, val cover: FrameLayout, val image: ImageView, val domain: TextView,
                 val title: TextView, val folder: TextView, val complete: Button, val menu: Button) : RecyclerView.ViewHolder(box)
    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = with(parent.context) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel(Color.WHITE)
            setPadding(dp(3), dp(3), dp(3), dp(6))
            elevation = dp(3).toFloat()
            layoutParams = StaggeredGridLayoutManager.LayoutParams(-1, -2).apply {
                setMargins(dp(5), dp(5), dp(5), dp(8))
            }
        }
        val cover = FrameLayout(this).apply { setBackgroundColor(accent) }
        val domain = label("", 15, true).apply { gravity = Gravity.CENTER }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        cover.addView(domain, FrameLayout.LayoutParams(-1, -1))
        cover.addView(image, FrameLayout.LayoutParams(-1, -1))
        box.addView(cover)
        val title = label("", 18, true).apply { maxLines = 3; setPadding(dp(9), dp(9), dp(9), 0) }
        val folder = label("", 13).apply { setPadding(dp(9), dp(4), dp(9), dp(8)) }
        box.addView(title)
        box.addView(folder)
        val row = LinearLayout(this)
        val complete = action("✓", Color.WHITE) {}
        val menu = action("•••", Color.WHITE) {}
        row.addView(complete, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(menu, LinearLayout.LayoutParams(0, dp(46), 1f))
        box.addView(row)
        Holder(box, cover, image, domain, title, folder, complete, menu)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder) {
        val item = items[position]
        val context = box.context
        (box.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = position % 7 == 4
        cover.layoutParams = LinearLayout.LayoutParams(-1, context.dp(listOf(126, 250, 175, 215, 160, 275, 145)[position % 7]))
        domain.text = URI(item.url).host
        image.load(item.image.takeIf { it.isNotBlank() }) { crossfade(true) }
        title.text = item.title
        folder.text = folders[item.folder]?.name ?: "Sem pasta"
        complete.text = if (item.done) "↶" else "✓"
        complete.contentDescription = (if (item.done) "Restaurar " else "Concluir ") + item.title
        complete.setOnClickListener { toggle(item) }
        menu.contentDescription = "Editar " + item.title
        menu.setOnClickListener { edit(item) }
        box.setOnClickListener { open(item) }
        if (item.image.isBlank()) requestPreview(item)
    }
}
