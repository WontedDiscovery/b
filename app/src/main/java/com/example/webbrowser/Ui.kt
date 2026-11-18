package com.example.webbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** 系統的水波紋效果資源；borderless 為圓形水波，否則為矩形水波 */
fun Context.rippleRes(borderless: Boolean): Int {
    val tv = TypedValue()
    val attr = if (borderless) {
        android.R.attr.selectableItemBackgroundBorderless
    } else {
        android.R.attr.selectableItemBackground
    }
    theme.resolveAttribute(attr, tv, true)
    return tv.resourceId
}

/** 網址只留網域名稱，例如 https://www.google.com/search → google.com */
fun hostOf(url: String): String {
    val host = try {
        Uri.parse(url).host
    } catch (e: Exception) {
        null
    }
    return host?.removePrefix("www.") ?: url
}

/** 清單中的一列 */
class Row(
    val title: String,
    val subtitle: String?,
    val trailing: Ico?,
    val onClick: () -> Unit,
    val onTrailing: (() -> Unit)? = null
)

class RowAdapter(private val rows: List<Row>, private val pal: Palette) :
    RecyclerView.Adapter<RowAdapter.Holder>() {

    class Holder(
        val root: LinearLayout,
        val title: TextView,
        val sub: TextView,
        val trailing: IconView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val title = TextView(ctx).apply {
            textSize = 16f
            setTextColor(pal.text)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val sub = TextView(ctx).apply {
            textSize = 13f
            setTextColor(pal.sub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(sub)
        }
        val trailing = IconView(ctx).apply {
            color = pal.sub
            isClickable = true
            setBackgroundResource(ctx.rippleRes(true))
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(8), ctx.dp(10))
            setBackgroundResource(ctx.rippleRes(false))
            isClickable = true
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(trailing, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
        }
        root.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return Holder(root, title, sub, trailing)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        val sub = row.subtitle
        holder.sub.text = sub ?: ""
        holder.sub.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        val icon = row.trailing
        if (icon != null) {
            holder.trailing.kind = icon
            holder.trailing.visibility = View.VISIBLE
        } else {
            holder.trailing.visibility = View.GONE
        }
        holder.root.setOnClickListener { row.onClick() }
        holder.trailing.setOnClickListener { row.onTrailing?.invoke() }
    }

    override fun getItemCount(): Int = rows.size
}

/** 分頁總覽的卡片 */
class TabCardAdapter(
    private val tabs: List<BrowserTab>,
    private val pal: Palette,
    private val currentIndex: () -> Int,
    private val onOpen: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabCardAdapter.Holder>() {

    class Holder(val card: LinearLayout, val title: TextView, val url: TextView, val preview: PreviewView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val title = TextView(ctx).apply {
            textSize = 15f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val url = TextView(ctx).apply {
            textSize = 12f
            setTextColor(pal.sub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val close = IconView(ctx).apply {
            kind = Ico.CLOSE
            color = pal.sub
            isClickable = true
            setBackgroundResource(ctx.rippleRes(true))
            contentDescription = ctx.getString(R.string.close_desc)
        }
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(ctx.dp(32), ctx.dp(32)))
        }
        val preview = PreviewView(ctx)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(6), ctx.dp(12))
            addView(top)
            addView(
                preview,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = ctx.dp(8)
                    bottomMargin = ctx.dp(8)
                }
            )
            addView(
                url,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(4) }
            )
        }
        card.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(200)
        ).apply { setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)) }

        val holder = Holder(card, title, url, preview)
        card.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onOpen(p)
        }
        close.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClose(p)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tab = tabs[position]
        val ctx = holder.card.context
        holder.title.text = tab.title.ifEmpty { hostOf(tab.url) }
        holder.url.text = if (StartPage.isStart(tab.url)) "" else hostOf(tab.url)
        holder.preview.bitmap = tab.preview
        val current = position == currentIndex()
        holder.card.background = GradientDrawable().apply {
            setColor(pal.card)
            cornerRadius = ctx.dp(18).toFloat()
            if (current) setStroke(ctx.dp(2), pal.accent)
        }
    }

    override fun getItemCount(): Int = tabs.size
}

/** 頁面頂部的標題列：返回鍵、標題、可選的右側按鈕 */
class Header(val view: LinearLayout, val title: TextView, val right: IconView?)

fun buildHeader(
    ctx: Context,
    pal: Palette,
    title: String,
    onBack: () -> Unit,
    right: Ico? = null,
    rightDesc: String? = null,
    onRight: (() -> Unit)? = null
): Header {
    val back = IconView(ctx).apply {
        kind = Ico.BACK
        color = pal.text
        contentDescription = ctx.getString(R.string.back_desc)
        isClickable = true
        setBackgroundResource(ctx.rippleRes(true))
        setOnClickListener { onBack() }
    }
    val titleView = TextView(ctx).apply {
        text = title
        textSize = 20f
        setTextColor(pal.text)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val bar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(6), ctx.dp(8), ctx.dp(8), ctx.dp(4))
        addView(back, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)))
        addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = ctx.dp(6)
            }
        )
    }
    var rightView: IconView? = null
    if (right != null) {
        val r = IconView(ctx).apply {
            kind = right
            color = pal.accent
            contentDescription = rightDesc
            isClickable = true
            setBackgroundResource(ctx.rippleRes(true))
            setOnClickListener { onRight?.invoke() }
        }
        bar.addView(r, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)))
        rightView = r
    }
    return Header(bar, titleView, rightView)
}

/** 底部選單中的一個項目 */
class SheetItem(
    val icon: Ico,
    val label: String,
    val checked: Boolean = false,
    val action: () -> Unit
)

/** 底部選單的卡片（可捲動），點項目時由呼叫端決定要做什麼 */
fun buildSheetCard(
    ctx: Context,
    pal: Palette,
    items: List<SheetItem>,
    onPick: (SheetItem) -> Unit,
    maxHeightPx: Int
): LinearLayout {
    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ctx.dp(8), 0, ctx.dp(8))
    }
    for (item in items) {
        val rowView = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(20), 0, ctx.dp(20), 0)
            setBackgroundResource(ctx.rippleRes(false))
            isClickable = true
            addView(
                IconView(ctx).apply {
                    kind = item.icon
                    color = pal.text
                },
                LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(24))
            )
            addView(
                TextView(ctx).apply {
                    text = item.label
                    textSize = 16f
                    setTextColor(pal.text)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = ctx.dp(16)
                }
            )
            if (item.checked) {
                addView(
                    IconView(ctx).apply {
                        kind = Ico.CHECK
                        color = pal.accent
                    },
                    LinearLayout.LayoutParams(ctx.dp(20), ctx.dp(20))
                )
            }
            setOnClickListener { onPick(item) }
        }
        rowView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(52)
        )
        col.addView(rowView)
    }
    val scroll = ScrollView(ctx).apply { addView(col) }
    val contentHeight = ctx.dp(52) * items.size + ctx.dp(16)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        background = GradientDrawable().apply {
            setColor(pal.card)
            cornerRadius = ctx.dp(22).toFloat()
        }
        addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(maxHeightPx, contentHeight)
            )
        )
    }
}

/** 分頁卡片裡的預覽圖：從頂端開始填滿、四角圓角；沒有圖時什麼都不畫 */
class PreviewView(ctx: Context) : View(ctx) {
    var bitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    private val radius = ctx.dp(12).toFloat()
    private val area = RectF()
    private val clip = Path()
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        area.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(area, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        val s = maxOf(w / b.width, h / b.height)
        drawMatrix.setScale(s, s)
        canvas.drawBitmap(b, drawMatrix, paint)
        canvas.restoreToCount(save)
    }
}
